package com.chinasofti.huateng.ticket.gate;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.RequestStationLineInfoResult;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.ticket.entrytxn.EntryTxnQueryService;
import com.chinasofti.huateng.ticket.station.StationLineResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * 支付宝出行 {@code industryDetail} 组装（按支付中心网关文档 V1.2「地铁免密 industryDetail」键集）。
 *
 * <p><b>键集分两批</b>：本类落的是「行程/订单固有属性」快照（进出站、时间、票种、累计类型等），
 * 落库后由 gate-txn-pay 在<b>每次发起扣款时</b>按入口注入 {@code payTriggerType}
 * （01-首次支付 / 02-补偿支付，见 {@code AlipayTripPayRequestFactory}）——
 * 补偿重推复用同一份快照，该键 NEVER 在本类写死。
 *
 * <p>{@code ticketTransSeq} 与 {@code tikcetTransSeq} <b>双键同值并存</b>：支付中心 V1.2 文档用前者，
 * 甲方 IF1A-01 规范样例用后者（{@code NotifyVerifyResultReqDTO#setTikcetTransSeq} 的兼容注释可证），
 * 对端按哪个拼写解析都能拿到。NEVER 删 {@code tikcetTransSeq}。
 */
@Component
class AlipayIndustryDetailAssembler {
    private static final Logger log = LoggerFactory.getLogger(AlipayIndustryDetailAssembler.class);

    private static final String ENTRY_ID_SUFFIX = "01";
    private static final String EXIT_ID_SUFFIX = "02";
    private static final DateTimeFormatter ORDER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int FORMATTED_DATE_LENGTH = 19;

    private final EntryTxnQueryService entryTxnQueryService;
    private final StationLineResolver stationLineResolver;
    private final String alipayCardIssueCode;
    private final Executor industryDetailExecutor;

    public AlipayIndustryDetailAssembler(
            EntryTxnQueryService entryTxnQueryService,
            StationLineResolver stationLineResolver,
            @Value("${alipay.trip.card-issue-code:0007}") String alipayCardIssueCode,
            @Qualifier("industryDetailExecutor") Executor industryDetailExecutor) {
        this.entryTxnQueryService = entryTxnQueryService;
        this.stationLineResolver = stationLineResolver;
        this.alipayCardIssueCode = alipayCardIssueCode;
        this.industryDetailExecutor = industryDetailExecutor;
    }
    /** 组装支付宝出行的 {@code industryDetail}，整块交给 gate-txn-pay-server 落单存下。 */
    public String assemble(NotifyVerifyResultReqDTO request) {
        try {
            String entryStationCode = request.getLastHandleStationCode();
            String exitStationCode = request.getHandleStationCode();
            CompletableFuture<String> entryDeviceFuture =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return entryTxnQueryService.queryEntryDevice(request.getCardId());
                        } catch (RuntimeException e) {
                            log.warn("查询进站设备异常, cardId={}", request.getCardId(), e);
                            return null;
                        }
                    }, industryDetailExecutor);
            CompletableFuture<RequestStationLineInfoResult> entryStationFuture =
                    CompletableFuture.supplyAsync(() -> queryStationLineInfo(entryStationCode), industryDetailExecutor);
            CompletableFuture<RequestStationLineInfoResult> exitStationFuture =
                    CompletableFuture.supplyAsync(() -> queryStationLineInfo(exitStationCode), industryDetailExecutor);

            Map<String, Object> stationInfo = buildStationInfoMap(
                    request, entryStationFuture.join(), exitStationFuture.join());
            applyEntryDeviceCode(stationInfo, entryDeviceFuture.join(), request.getCardId());
            stationInfo.put("cardIssueCode", alipayCardIssueCode);

            String industryDetail = JSON.toJSONString(stationInfo);
            log.info("IF1A-01 支付宝出行行业明细已组装, cardId={}, industryDetail={}",
                    request.getCardId(), industryDetail);
            return industryDetail;
        } catch (Exception e) {
            log.error("IF1A-01 支付宝出行行业明细组装异常，本笔仍落单但扣费会落 RETRY, cardId={}, trxType={}, handleDateTime={}",
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime(), e);
            return null;
        }
    }
    /** 把已查回的进出站线路信息合并为支付宝 industryDetail Map。 */
    private Map<String, Object> buildStationInfoMap(NotifyVerifyResultReqDTO request,
                                                    RequestStationLineInfoResult entryStationInfo,
                                                    RequestStationLineInfoResult exitStationInfo) {
        String entryStationCode = request.getLastHandleStationCode();
        String exitStationCode = request.getHandleStationCode();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("orderDate", resolveOrderDate(request));
        detail.put("cardNum", blankIfNull(request.getCardId()));
        detail.put("cardType", blankIfNull(request.getCardType()));
        detail.put("phone", blankIfNull(request.getPhone()));
        detail.put("channelAgreementNo", "");
        // 双键同值：V1.2 文档键名 + 甲方规范样例拼写，对端按哪个解析都能拿到。
        detail.put("ticketTransSeq", blankIfNull(request.getTicketTransSeq()));
        detail.put("tikcetTransSeq", blankIfNull(request.getTicketTransSeq()));
        detail.put("ticketFlag", resolveTicketFlag(request));
        detail.put("cumulativeType", resolveCumulativeType(request));

        detail.put("entryLineCode", blankIfNull(getOrFallback(entryStationInfo, RequestStationLineInfoResult::getLineCode, entryStationCode)));
        detail.put("entryLineName", blankIfNull(getOrFallback(entryStationInfo, RequestStationLineInfoResult::getLineName, entryStationCode)));
        detail.put("entryStationCode", blankIfNull(entryStationCode));
        detail.put("entryStationName", blankIfNull(getOrFallback(entryStationInfo, RequestStationLineInfoResult::getStationName, entryStationCode)));
        detail.put("entryDeviceCode", "");
        detail.put("entryDate", blankIfNull(convertHandleDateTime(request.getLastHandleDateTime())));
        detail.put("entryId", blankIfNull(buildTripId(request.getItpUserId(), request.getLastHandleDateTime(), ENTRY_ID_SUFFIX)));

        detail.put("exitId", blankIfNull(buildTripId(request.getItpUserId(), request.getHandleDateTime(), EXIT_ID_SUFFIX)));
        detail.put("exitLineCode", blankIfNull(getOrFallback(exitStationInfo, RequestStationLineInfoResult::getLineCode, exitStationCode)));
        detail.put("exitLineName", blankIfNull(getOrFallback(exitStationInfo, RequestStationLineInfoResult::getLineName, exitStationCode)));
        detail.put("exitStationCode", blankIfNull(exitStationCode));
        detail.put("exitStationName", blankIfNull(getOrFallback(exitStationInfo, RequestStationLineInfoResult::getStationName, exitStationCode)));
        detail.put("exitDeviceCode", blankIfNull(request.getDeviceId()));
        detail.put("exitDate", blankIfNull(convertHandleDateTime(request.getHandleDateTime())));

        detail.put("orderExpType", "03".equals(request.getTrxType()) ? "5" : "0");
        putOverTimeAmount(detail, request);
        return detail;
    }

    /**
     * 票据标志：普通 N，同行票 Y，其他合作方票 C（V1.2 文档）。
     * 直接映射 {@code companionFlag}（空=普通票），组装前已由 {@code GateCardTypeEnricher}
     * 用账户侧数据规范化。
     */
    private String resolveTicketFlag(NotifyVerifyResultReqDTO request) {
        return StringUtils.hasText(request.getCompanionFlag()) ? request.getCompanionFlag().trim() : "N";
    }

    /**
     * 累计类型：01-普通闸机出站，02-闸机超时出站，03-其他不参与累计的票（NFC、同行票等）（V1.2 文档）。
     * 票种属性优先：同行票/第三方票不参与累计（03）；其余按是否超时出站分流（trxType=03 → 02）。
     */
    private String resolveCumulativeType(NotifyVerifyResultReqDTO request) {
        if (StringUtils.hasText(request.getCompanionFlag())) {
            return "03";
        }
        return "03".equals(request.getTrxType()) ? "02" : "01";
    }

    /**
     * 超时补收金额（单位：分），V1.2 文档类型为 int——MUST 以数字出网，NEVER 带引号。
     * 解析失败则缺键 + WARN：对端该键非必填，收到错误值的危害大于缺失。
     */
    private void putOverTimeAmount(Map<String, Object> detail, NotifyVerifyResultReqDTO request) {
        String amount = request.getOvertimeAmount();
        if (!StringUtils.hasText(amount)) {
            return;
        }
        try {
            detail.put("overTimeAmount", Integer.valueOf(amount.trim()));
        } catch (NumberFormatException e) {
            log.warn("IF1A-01 超时金额非数字，overTimeAmount 缺省, cardId={}, overtimeAmount={}",
                    request.getCardId(), amount);
        }
    }
    /**
     * {@code orderDate} 取本次出站时刻（与 {@code exitDate} 同源同值）。
     *
     * <p>支付中心的必填项 {@code orderTime} 由这个键派生 —— 我方**不单独送 orderTime 字段**，
     * 缺这个键对端返 {@code retCode=10002 交易时间orderTime不可为空}（2026-09-18 实测）。
     * <b>NEVER 改回 {@code LocalDateTime.now()}</b>：那样一旦走扫表补偿重推，orderDate 会漂到
     * 重推时刻、与同报文里的 {@code exitDate} 分叉，渠道账本上同一笔行程出现两个交易时间。
     *
     * <p>闸机没送出站时间（或格式短于 14 位）时才回落当前时刻并留 WARN：这个键
     * <b>NEVER 允许落空串</b>，空串对支付中心等于缺失、照样返 10002。
     */
    private String resolveOrderDate(NotifyVerifyResultReqDTO request) {
        String exitDate = convertHandleDateTime(request.getHandleDateTime());
        if (exitDate != null && exitDate.length() >= FORMATTED_DATE_LENGTH) {
            return exitDate;
        }
        log.warn("IF1A-01 出站时间缺失或格式异常，orderDate 回落当前时刻, cardId={}, handleDateTime={}",
                request.getCardId(), request.getHandleDateTime());
        return LocalDateTime.now().format(ORDER_DATE_FORMATTER);
    }

    /** industryDetail 的字段值 */
    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    /** 注入进站设备号。 */
    private void applyEntryDeviceCode(Map<String, Object> stationInfo, String entryDeviceCode, String cardId) {
        if (StringUtils.hasText(entryDeviceCode)) {
            stationInfo.put("entryDeviceCode", entryDeviceCode.trim());
            return;
        }
        log.warn("IF1A-01 未取到进站设备号，industryDetail 的 entryDeviceCode 置空串, cardId={}", cardId);
    }

    private <T> String getOrFallback(T info, Function<T, String> getter, String fallback) {
        if (info == null) return fallback;
        String value = getter.apply(info);
        return StringUtils.hasText(value) ? value : fallback;
    }

    /** 查询车站线路信息：stationCode 为空时直接返回 null；调用失败时 {@link StationLineResolver} 内部记 warn 并返回 null，由调用方用 stationCode 自身兜底。 */
    private RequestStationLineInfoResult queryStationLineInfo(String stationCode) {
        return stationLineResolver.resolveLineInfo(stationCode);
    }
    /** 将设备上报的时间字符串（yyyyMMddHHmmss...）转换为友好格式（yyyy-MM-dd HH:mm:ss）。 */
    private String convertHandleDateTime(String handleDateTime) {
        if (handleDateTime == null || handleDateTime.length() < 14) {
            return handleDateTime;
        }
        return handleDateTime.substring(0, 4) + "-" +
                handleDateTime.substring(4, 6) + "-" +
                handleDateTime.substring(6, 8) + " " +
                handleDateTime.substring(8, 10) + ":" +
                handleDateTime.substring(10, 12) + ":" +
                handleDateTime.substring(12, 14);
    }

    /** 构建进站/出站唯一标识：itpUserId（已转十进制） + 紧凑日期（yyyyMMddHHmmss） + 后缀(01/02)。 */
    private String buildTripId(String itpUserId, String handleDateTime, String suffix) {
        String dateCompact = compactDate(convertHandleDateTime(handleDateTime));
        return itpUserId + (StringUtils.hasText(dateCompact) ? dateCompact : "") + suffix;
    }

    /** 从格式化日期字符串中抽离纯数字，取前14位（yyyyMMddHHmmss），用于拼接 tripId。 */
    private String compactDate(String date) {
        if (!StringUtils.hasText(date)) {
            return "";
        }
        String compact = date.replaceAll("[^0-9]", "");
        return compact.length() > 14 ? compact.substring(0, 14) : compact;
    }
}
