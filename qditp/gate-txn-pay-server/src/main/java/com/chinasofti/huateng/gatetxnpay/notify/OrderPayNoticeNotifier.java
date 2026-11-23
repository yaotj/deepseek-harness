package com.chinasofti.huateng.gatetxnpay.notify;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.gatetxnpay.constant.DebitStatus;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.app.AppOrderPayNoticeReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * R6 §3.62「行程订单通知」/app/orderPayNotice：行程订单产生支付结果后（无论失败还是成功）ITP 推给 APP。
 *
 * <p>与同族 §3.63 / 卡片行程通知同形态：multipart 表单公共参数（providerId/charset/format/timestamp/deviceId/
 * signType/sign）+ {@code bizData} 业务报文；受理判据 HTTP 2xx 且 {@code retCode=0000}；
 * 失败由 {@link GateTxnPayNoticeService} 退避重试，本类只负责一次投递。</p>
 *
 * <p><b>站时间 MUST 是 {@code yyyyMMddHHmmss} 全 14 位</b>（2026-11-20 APP 侧明确要求）：{@code entryStationDate} /
 * {@code exitStationDate} 虽名为 Date，但传 8 位 {@code yyyyMMdd} 会让 APP 解析异常、直接落兜底码
 * {@code 7004 处理过程出现错误!}；闸机上送的 {@code IN_TIME} / {@code OUT_TIME} 本就是 14 位，NEVER 再截断。</p>
 *
 * <p>单号口径（规范未给字典，本地定稿）：
 * {@code tradeOrderNo} = 过闸交易订单号（GT 开头的 GATE_TXN_PAY.ORDER_NO）；
 * {@code payTradeOrderNo} / {@code requestDebitNo} 本应由支付域回传（支付中心单号、扣款请求流水），
 * 当前 gate 侧无该数据来源，按订单号兜底，待支付域回传后替换。</p>
 */
@Service
public class OrderPayNoticeNotifier extends ProxyWebClient {

    private static final Logger log = LoggerFactory.getLogger(OrderPayNoticeNotifier.class);

    static final String LINK_NAME = "行程订单通知";
    static final String APP_GATEWAY_SUCCESS_RET_CODE = "0000";
    static final String ORDER_STATUS_SUCCESS = "0";
    static final String ORDER_STATUS_FAIL = "2";

    /** 站时间对外格式，APP 侧 2026-11-20 明确要求 {@code yyyyMMddHHmmss}（传 8 位日期直接返 7004）。 */
    static final String STATION_DATE_TIME_PATTERN = "yyyyMMddHHmmss";
    private static final int DATE_TIME_LENGTH = 14;
    private static final int DATE_LENGTH = 8;
    private static final String ZERO_TIME_SUFFIX = "000000";

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern(STATION_DATE_TIME_PATTERN);
    private static final DateTimeFormatter DASHED_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String url;
    private final boolean enabled;
    private final String providerId;
    private final String charset;
    private final String format;
    private final String signType;
    private final String sign;

    public OrderPayNoticeNotifier(
            @Value("${app.notify.order-pay-notice-url:}") String url,
            @Value("${app.notify.order-pay-notice-enabled:false}") boolean enabled,
            @Value("${app.notify.order-pay-notice-open-logger:false}") boolean openLogger,
            @Value("${app.notify.order-pay-notice-timeout-ms:3000}") long timeoutMs,
            @Value("${app.notify.provider-id:01}") String providerId,
            @Value("${app.notify.charset:UTF-8}") String charset,
            @Value("${app.notify.format:json}") String format,
            @Value("${app.notify.sign-type:00}") String signType,
            @Value("${app.notify.sign:}") String sign,
            WebClient.Builder webClientBuilder) {
        super(url, openLogger, webClientBuilder, Duration.ofMillis(timeoutMs));
        this.url = url;
        this.enabled = enabled;
        this.providerId = providerId;
        this.charset = charset;
        this.format = format;
        this.signType = signType;
        this.sign = sign;
    }

    /**
     * 推送一笔已收敛到终态的行程订单。调用方负责保证 status ∈ {SUCCESS, FAIL}。
     */
    public boolean push(GateTxnPay order, String debitStatus) {
        if (!enabled) {
            log.info("{}已关闭, orderNo={}, debitStatus={}", LINK_NAME, order.getOrderNo(), debitStatus);
            return false;
        }
        if (!StringUtils.hasText(url)) {
            log.warn("{}地址未配置（app.notify.order-pay-notice-url 为空），本次跳过, orderNo={}",
                    LINK_NAME, order.getOrderNo());
            return false;
        }

        AppOrderPayNoticeReqDTO notifyRequest = buildNotifyRequest(order, debitStatus);
        String bizData = JSON.toJSONString(notifyRequest);
        log.info("调用{}, orderNo={}, debitStatus={}, url={}, bizData={}",
                LINK_NAME, order.getOrderNo(), debitStatus, url, bizData);
        try {
            String response = postMultipartFormAndGetResponse(url, buildForm(bizData, order.getDeviceId()), null);
            boolean accepted = isAccepted(response);
            if (accepted) {
                log.info("调用{}成功, orderNo={}, response={}", LINK_NAME, order.getOrderNo(), response);
            } else {
                log.warn("调用{}未受理, orderNo={}, response={}", LINK_NAME, order.getOrderNo(), response);
            }
            return accepted;
        } catch (RuntimeException e) {
            log.error("调用{}异常, orderNo={}, url={}", LINK_NAME, order.getOrderNo(), url, e);
            return false;
        }
    }

    AppOrderPayNoticeReqDTO buildNotifyRequest(GateTxnPay order, String debitStatus) {
        AppOrderPayNoticeReqDTO notifyRequest = new AppOrderPayNoticeReqDTO();
        notifyRequest.setThirdUserId(order.getThirdUserId());
        notifyRequest.setEntryStationName(resolveStationName(order.getEntryStationName(), order.getInStation(), "进站"));
        notifyRequest.setExitStationName(resolveStationName(order.getExitStationName(), order.getOutStation(), "出站"));
        notifyRequest.setEntryStationDate(resolveDate(order.getInTime(), "inTime", order.getOrderNo()));
        notifyRequest.setExitStationDate(resolveDate(order.getOutTime(), "outTime", order.getOrderNo()));
        notifyRequest.setPayAmount(amountOf(order.getTotalAmount()));
        notifyRequest.setTradeOrderNo(order.getOrderNo());
        notifyRequest.setPayTradeOrderNo(order.getOrderNo());
        notifyRequest.setOrderExpType(order.getOrderExpType());
        notifyRequest.setPayChannelCode(resolvePayChannelCode(order));
        notifyRequest.setOrderStatus(toAppOrderStatus(debitStatus));
        notifyRequest.setRequestDebitNo(order.getOrderNo());
        notifyRequest.setCardNo(order.getCardId());
        notifyRequest.setTicketType(order.getCardType());
        notifyRequest.setTransSeq(order.getTicketTransSeq());
        notifyRequest.setDebitAmount(amountOf(DebitStatus.SUCCESS.is(debitStatus) ? order.getTotalAmount() : 0));
        return notifyRequest;
    }

    MultipartBodyBuilder buildForm(String bizData, String deviceId) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("providerId", providerId);
        builder.part("charset", charset);
        builder.part("format", format);
        builder.part("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        builder.part("deviceId", deviceId == null ? "" : deviceId);
        builder.part("signType", signType);
        builder.part("sign", sign == null ? "" : sign);
        builder.part("bizData", bizData);
        return builder;
    }

    boolean isAccepted(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return false;
        }
        try {
            return APP_GATEWAY_SUCCESS_RET_CODE.equals(JSON.parseObject(responseBody).getString("retCode"));
        } catch (RuntimeException e) {
            log.warn("{}应答无法解析为 JSON, response={}", LINK_NAME, responseBody);
            return false;
        }
    }

    static String toAppOrderStatus(String debitStatus) {
        return DebitStatus.SUCCESS.is(debitStatus) ? ORDER_STATUS_SUCCESS : ORDER_STATUS_FAIL;
    }

    private String resolveStationName(String stationName, String stationCode, String direction) {
        if (StringUtils.hasText(stationName)) {
            return stationName;
        }
        log.warn("{}缺{}站名，按站码兜底, stationCode={}", LINK_NAME, direction, stationCode);
        return stationCode;
    }

    private String resolveDate(String dateTime, String fieldName, String orderNo) {
        if (!StringUtils.hasText(dateTime)) {
            log.warn("{}缺{}，时间留空, orderNo={}", LINK_NAME, fieldName, orderNo);
            return "";
        }
        String raw = dateTime.trim();
        if (isDigits(raw, DATE_TIME_LENGTH)) {
            return raw;
        }
        if (isDigits(raw, DATE_LENGTH)) {
            return raw + ZERO_TIME_SUFFIX;
        }
        try {
            return LocalDateTime.parse(raw, DASHED_DATE_TIME_FORMATTER).format(TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("{}的{}不是可识别的时间格式（要求 {}），按原值上报, orderNo={}, value={}",
                    LINK_NAME, fieldName, STATION_DATE_TIME_PATTERN, orderNo, raw);
            return raw;
        }
    }

    private boolean isDigits(String value, int length) {
        return value.length() == length && value.chars().allMatch(Character::isDigit);
    }

    private String resolvePayChannelCode(GateTxnPay order) {
        if (StringUtils.hasText(order.getPaymentVendor())) {
            return order.getPaymentVendor();
        }
        return order.getSignChannelCode();
    }

    private String amountOf(Integer amount) {
        return String.valueOf(amount == null ? 0 : amount);
    }
}
