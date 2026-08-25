package com.chinasofti.huateng.fep.dev.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.dev.constant.FepDevErrorCodeEnum;
import com.chinasofti.huateng.model.app.RequestStationLineInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestStationLineInfoResult;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.para.ParaClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * IF1A-01 闸机出站交易处理。
 * 流程：参数校验 → 判断支付宝/普通交易 → 解析票卡类型 → 调 ticket-server 保存检票记录 → 按需触发扣费。
 */
@Component
public class GateTransactionHandler {
    private static final Logger log = LoggerFactory.getLogger(GateTransactionHandler.class);
    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final TicketClient ticketClient;
    private final GateTxnPayClient gateTxnPayClient;
    private final AlipayPaySignClient alipayPaySignClient;
    private final AlipayAccountClient alipayAccountClient;
    private final AccountClient accountClient;
    private final ParaClient paraClient;
    private final String payCallbackUrl;
    private final String alipayScene;
    private final String alipayPaymentVendor;
    private final String alipayIndustryType;
    private final String alipaySubject;
    private final String alipayBody;
    private final int alipayOrderTimeoutMinutes;
    private static final String ENTRY_ID_SUFFIX = "01";
    private static final String EXIT_ID_SUFFIX = "02";

    public GateTransactionHandler(
            TicketClient ticketClient,
            GateTxnPayClient gateTxnPayClient,
            AlipayPaySignClient alipayPaySignClient,
            AlipayAccountClient alipayAccountClient,
            AccountClient accountClient,
            ParaClient paraClient,
            @Value("${pay.center.callback-url:http://fep-alipay-server:8080/api/payment/payNotify}") String payCallbackUrl,
            @Value("${alipay.trip.scene:TRIP}") String alipayScene,
            @Value("${alipay.trip.payment.vendor:05}") String alipayPaymentVendor,
            @Value("${alipay.trip.industry.type:1}") String alipayIndustryType,
            @Value("${alipay.trip.subject:地铁乘车扣费}") String alipaySubject,
            @Value("${alipay.trip.body:地铁乘车费用}") String alipayBody,
            @Value("${alipay.trip.order.timeout.minutes:60}") int alipayOrderTimeoutMinutes) {
        this.ticketClient = ticketClient;
        this.gateTxnPayClient = gateTxnPayClient;
        this.alipayPaySignClient = alipayPaySignClient;
        this.alipayAccountClient = alipayAccountClient;
        this.accountClient = accountClient;
        this.paraClient = paraClient;
        this.payCallbackUrl = payCallbackUrl;
        this.alipayScene = alipayScene;
        this.alipayPaymentVendor = alipayPaymentVendor;
        this.alipayIndustryType = alipayIndustryType;
        this.alipaySubject = alipaySubject;
        this.alipayBody = alipayBody;
        this.alipayOrderTimeoutMinutes = alipayOrderTimeoutMinutes;
    }

    /**
     * IF1A-01 闸机检票通知：核心出站交易入口。
     * 流程：参数校验 → 判断支付宝/普通交易 → 解析票卡类型 → 调 ticket-server 保存检票记录 → 按需触发扣费。
     * 支付宝交易走 requestAlipayTripPay，普通票走 requestGateTxnPay。
     * ticket-server 返回非 0000 时直接透传响应，不触发任何扣费操作。
     */
    public NotifyVerifyResultRespDTO notifyVerifyResult(NotifyVerifyResultReqDTO request) {
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            response.setRetCode(FepDevErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("cardId不能为空");
            return response;
        }

        boolean alipayTransaction = isAlipayTransaction(request);
        String itpCardType = alipayTransaction ? null : resolveCardType(request);
        request.setItpUserId(normalizeThirdUserId(request.getItpUserId(), alipayTransaction));

        log.info("IF1A-01 调用 ticket-server 闸机检票通知, cardId={}, trxType={}", request.getCardId(), request.getTrxType());
        NotifyVerifyResultRespDTO ticketResponse = ticketClient.notifyVerifyResult(request);
        log.info("IF1A-01 调用 ticket-server 闸机检票通知, 返回={}", JSON.toJSONString(ticketResponse));
        if (ticketResponse == null) {
            log.warn("IF1A-01 ticket-server 无响应, cardId={}", request.getCardId());
            return buildSystemErrorResponse(response);
        }

        if (!"0000".equals(ticketResponse.getRetCode())) {
            // ticket-server 处理失败（参数校验、二维码不存在等），直接透传，不触发扣费
            log.warn("IF1A-01 ticket-server 处理失败, cardId={}, retCode={}, retMsg={}",
                    request.getCardId(), ticketResponse.getRetCode(), ticketResponse.getRetMsg());
            return ticketResponse;
        }

        // ticket-server 成功：按需触发扣费
        if (shouldPay(request)) {
            log.info("IF1A-01 出站交易调用扣费交易服务, cardId={}, alipay={}", request.getCardId(), alipayTransaction);
            if (alipayTransaction) {
                requestAlipayTripPay(request);
            } else {
                requestGateTxnPay(request, ticketResponse);
            }
        }
        return ticketResponse;
    }

    private NotifyVerifyResultRespDTO buildSystemErrorResponse(NotifyVerifyResultRespDTO template) {
        template.setRetCode(FepDevErrorCodeEnum.SYSTEM_ERROR.getCode());
        template.setRetMsg("闸机检票通知服务异常");
        return template;
    }

    /**
     * 非支付宝交易时，按 cardId 查询 account-server 获取真实票卡类型（itpCardType）。
     * 鲁通码虽然支付通道为支付宝 03，但发行渠道仍走普通 APP 推送，不进入 issueChannelCode=07 的支付宝码路径。
     * 失败或返回异常时返回 null，调用方据此跳过扣费。同时将 cardType 回写到 request 供后续使用。
     */
    private String resolveCardType(NotifyVerifyResultReqDTO request) {
        try {
            log.info("IF1A-01 非支付宝交易按cardId查询真实卡类型, cardId={}", request.getCardId());
            QueryUserInfoResult userInfo = accountClient.queryCardTypeByCardId(request.getCardId());
            log.info("IF1A-01 非支付宝交易按cardId查询真实卡类型, 返回={}", JSON.toJSONString(userInfo));
            if (userInfo == null || !"0000".equals(userInfo.getRetCode())
                    || !StringUtils.hasText(userInfo.getCardType())
                    || !StringUtils.hasText(userInfo.getItpCardType())) {
                return null;
            }
            request.setCardType(userInfo.getCardType().trim());
            return userInfo.getItpCardType().trim();
        } catch (Exception e) {
            log.error("IF1A-01 非支付宝交易查询真实卡类型异常, cardId={}, itpUserId={}",
                    request.getCardId(), request.getItpUserId(), e);
            return null;
        }
    }

    /**
     * 判断是否为支付宝交易：issueChannelCode == "07" 表示支付宝渠道。
     */
    private boolean isAlipayTransaction(NotifyVerifyResultReqDTO request) {
        return request != null && IssueChannelCodeEnum.isAlipay(request.getIssueChannelCode());
    }

    /**
     * 发起支付宝出行扣费：并行查询用户信息、进站设备、进出站线路信息，
     * 组装 industryDetail 后调用 alipay-pay-sign-server 完成扣费。
     * 异常时仅记录 error 日志，不抛出（调用方不依赖此返回值）。
     */
    private void requestAlipayTripPay(NotifyVerifyResultReqDTO request) {
        try {
            // 并行发起三个独立 RPC 调用
            CompletableFuture<AlipayUserInfoDTO> userFuture =
                    CompletableFuture.supplyAsync(() -> alipayAccountClient.selectByThirdUserId(request.getItpUserId()));
            CompletableFuture<String> entryDeviceFuture =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return ticketClient.queryEntryDevice(request.getCardId());
                        } catch (RuntimeException e) {
                            log.warn("查询进站设备异常, cardId={}", request.getCardId(), e);
                            return null;
                        }
                    });
            CompletableFuture<Map<String, Object>> stationInfoFuture =
                    CompletableFuture.supplyAsync(() -> buildStationInfoMap(request));

            AlipayUserInfoDTO userInfo = userFuture.get();
            String phone = userInfo != null ? userInfo.getPhone() : null;
            String entryDeviceCode = entryDeviceFuture.get();
            Map<String, Object> stationInfo = stationInfoFuture.get();

            AlipayTripRequestPayReqDTO payRequest = new AlipayTripRequestPayReqDTO();
            payRequest.setOrderNo(buildAlipayOrderNo(request.getCardId()));
            payRequest.setScene(alipayScene);
            payRequest.setPaymentVendor(alipayPaymentVendor);
            payRequest.setAmount(parseAlipayAmount(request.getTrxAmount(), request.getOvertimeAmount()));
            payRequest.setIndustryType(alipayIndustryType);
            payRequest.setSubject(alipaySubject);
            payRequest.setBody(alipayBody);
            payRequest.setRequestSignSeq(request.getTicketTransSeq());
            payRequest.setThirdUserId(request.getItpUserId());
            payRequest.setOrderTimeOut(alipayOrderTimeoutMinutes);
            payRequest.setNotifyUrl(payCallbackUrl);
            payRequest.setIndustryDetail(JSON.toJSONString(stationInfo));

            log.info("支付宝交通乘车码扣费请求, 入参={}", JSON.toJSONString(payRequest));
            AlipayTripRequestPayRespDTO payResponse = alipayPaySignClient.alipayTripRequestPay(payRequest);
            log.info("支付宝交通乘车码扣费响应, 返回={}", JSON.toJSONString(payResponse));
        } catch (Exception e) {
            log.error("支付宝交通乘车码扣费异常, cardId={}, trxType={}, handleDateTime={}",
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime(), e);
        }
    }

    /**
     * 并行查询进出站线路信息并合并为支付宝 industryDetail Map。
     * entryDate 从 lastHandleDateTime 转换，exitDate 从 handleDateTime 转换；
     * entryId/exitId 由各辅助方法构建；orderExpType 根据 trxType 是否为超时出站决定。
     */
    private Map<String, Object> buildStationInfoMap(NotifyVerifyResultReqDTO request) {
        String entryStationCode = request.getLastHandleStationCode();
        String exitStationCode = request.getHandleStationCode();

        CompletableFuture<RequestStationLineInfoResult> entryFuture =
                CompletableFuture.supplyAsync(() -> queryStationLineInfo(entryStationCode));
        CompletableFuture<RequestStationLineInfoResult> exitFuture =
                CompletableFuture.supplyAsync(() -> queryStationLineInfo(exitStationCode));

        RequestStationLineInfoResult entryStationInfo = entryFuture.join();
        RequestStationLineInfoResult exitStationInfo = exitFuture.join();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("orderDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        detail.put("cardNum", request.getCardId());
        detail.put("channelAgreementNo", null);
        detail.put("tikcetTransSeq", request.getTicketTransSeq());

        detail.put("entryLineCode", getOrFallback(entryStationInfo, RequestStationLineInfoResult::getLineCode, entryStationCode));
        detail.put("entryLineName", getOrFallback(entryStationInfo, RequestStationLineInfoResult::getLineName, entryStationCode));
        detail.put("entryStationCode", entryStationCode);
        detail.put("entryStationName", getOrFallback(entryStationInfo, RequestStationLineInfoResult::getStationName, entryStationCode));
        detail.put("entryDeviceCode", null); // 由调用方注入
        detail.put("entryDate", convertHandleDateTime(request.getLastHandleDateTime()));
        detail.put("entryId", buildTripId(request.getItpUserId(), request.getLastHandleDateTime(), ENTRY_ID_SUFFIX));

        detail.put("exitId", buildTripId(request.getItpUserId(), request.getHandleDateTime(), EXIT_ID_SUFFIX));
        detail.put("exitLineCode", getOrFallback(exitStationInfo, RequestStationLineInfoResult::getLineCode, exitStationCode));
        detail.put("exitLineName", getOrFallback(exitStationInfo, RequestStationLineInfoResult::getLineName, exitStationCode));
        detail.put("exitStationCode", exitStationCode);
        detail.put("exitStationName", getOrFallback(exitStationInfo, RequestStationLineInfoResult::getStationName, exitStationCode));
        detail.put("exitDeviceCode", request.getDeviceId());
        detail.put("exitDate", convertHandleDateTime(request.getHandleDateTime()));

        detail.put("orderExpType", "03".equals(request.getTrxType()) ? "5" : "0");
        detail.put("fineAmount", request.getOvertimeAmount());
        return detail;
    }

    private <T> String getOrFallback(T info, Function<T, String> getter, String fallback) {
        if (info == null) return fallback;
        String value = getter.apply(info);
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * 构建支付宝订单号：前缀"GT" + 当前时间（yyyyMMddHHmmssSSS）+ 卡号末6位。
     */
    private String buildAlipayOrderNo(String cardId) {
        String time = LocalDateTime.now().format(ORDER_TIME_FORMATTER);
        String suffix = cardId;
        if (suffix != null && suffix.length() > 6) {
            suffix = suffix.substring(suffix.length() - 6);
        }
        return "GT" + time + (suffix == null ? "" : suffix);
    }

    /**
     * 解析支付宝扣费金额（分）：交易金额 + 超时金额，任一为空则按 0 处理。
     */
    private Integer parseAlipayAmount(String trxAmount, String overtimeAmount) {
        return parseAmount(trxAmount) + parseAmount(overtimeAmount);
    }

    /**
     * 查询车站线路信息：stationCode 为空时直接返回 null；调用失败时记录 warn 并返回 null，
     * 由调用方用 stationCode 自身作为 fallback 兜底。
     */
    private RequestStationLineInfoResult queryStationLineInfo(String stationCode) {
        if (!StringUtils.hasText(stationCode)) {
            return null;
        }
        try {
            RequestStationLineInfoReqDTO req = new RequestStationLineInfoReqDTO();
            req.setStationCode(stationCode);
            return paraClient.requestStationLineInfo(req);
        } catch (Exception e) {
            log.warn("查询车站线路信息失败, stationCode={}", stationCode, e);
            return null;
        }
    }

    /**
     * 将设备上报的时间字符串（yyyyMMddHHmmss...）转换为友好格式（yyyy-MM-dd HH:mm:ss）。
     * 长度不足14位或为 null 时原样返回，不抛异常。
     */
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

    /**
     * 构建进站/出站唯一标识：itpUserId（已转十进制） + 紧凑日期（yyyyMMddHHmmss） + 后缀(01/02)。
     * 日期部分为空时不使用，保证ID至少由用户ID+后缀构成。
     */
    private String buildTripId(String itpUserId, String handleDateTime, String suffix) {
        String dateCompact = compactDate(convertHandleDateTime(handleDateTime));
        return itpUserId + (StringUtils.hasText(dateCompact) ? dateCompact : "") + suffix;
    }

    /**
     * 从格式化日期字符串中抽离纯数字，取前14位（yyyyMMddHHmmss），用于拼接 tripId。
     */
    private String compactDate(String date) {
        if (!StringUtils.hasText(date)) {
            return "";
        }
        String compact = date.replaceAll("[^0-9]", "");
        return compact.length() > 14 ? compact.substring(0, 14) : compact;
    }

    /**
     * 解析金额字符串为整数（分），空值返回 0。
     * 注意：若字符串非纯数字会抛出 NumberFormatException，由调用方 catch 兜底。
     */
    private int parseAmount(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }

    /**
     * 将十六进制 itpUserId 转换为十进制字符串，并按交易类型补齐零位：
     * 支付宝交易补至 10 位，普通交易补至 8 位。转换失败时原值返回并记录 warn 日志。
     */
    private String normalizeThirdUserId(String itpUserId, boolean alipayTransaction) {
        if (!StringUtils.hasText(itpUserId)) {
            return itpUserId;
        }
        String decimal;
        try {
            decimal = new BigInteger(itpUserId.trim(), 16).toString(10);
        } catch (Exception e) {
            log.warn("IF1A-01 itpUserId十六进制转十进制失败, itpUserId={}", itpUserId);
            return itpUserId;
        }
        return alipayTransaction ? leftPadToTen(decimal) : leftPadToEight(decimal);
    }

    /**
     * 左侧补零至 8 位，已满足长度或为空时原值返回。
     */
    private String leftPadToEight(String value) {
        if (!StringUtils.hasText(value) || value.length() >= 8) {
            return value;
        }
        return "0".repeat(8 - value.length()) + value;
    }

    /**
     * 左侧补零至 10 位，已满足长度或为空时原值返回。
     */
    private String leftPadToTen(String value) {
        if (!StringUtils.hasText(value) || value.length() >= 10) {
            return value;
        }
        return "0".repeat(10 - value.length()) + value;
    }

    /**
     * 判断是否需要发起扣费：仅 trxType 为 "02"（正常出站）或 "03"（超时出站）时触发。
     * 其他类型（如异常开闸、免费放行）不扣费。
     */
    private boolean shouldPay(NotifyVerifyResultReqDTO request) {
        return request != null && TrxTypeCodeEnum.isExitTxn(request.getTrxType());
    }

    /**
     * 调 gate-txn-pay-server 入库出站扣费交易记录。
     * 支付宝路径由 requestAlipayTripPay 调用；非支付宝路径由 notifyVerifyResult 直接调用。
     * 异常时记录 error 日志，不向上抛出（入库失败由对账补偿）。
     */
    private void requestGateTxnPay(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO ticketResponse) {
        try {
            GateTxnPayReqDTO payRequest = new GateTxnPayReqDTO();
            payRequest.setDeviceId(request.getDeviceId());
            payRequest.setItpUserId(request.getItpUserId());
            payRequest.setTrxType(request.getTrxType());
            payRequest.setIssueChannelCode(request.getIssueChannelCode());
            payRequest.setSignChannelCode(request.getSignChannelCode());
            payRequest.setCardId(request.getCardId());
            payRequest.setCardType(request.getCardType());
            payRequest.setHandleDateTime(request.getHandleDateTime());
            payRequest.setHandleStationCode(request.getHandleStationCode());
            payRequest.setTrxAmount(request.getTrxAmount());
            payRequest.setOvertimeAmount(request.getOvertimeAmount());
            payRequest.setLastTicketStatus(request.getLastTicketStatus());
            payRequest.setHandleResultCode(request.getHandleResultCode());
            payRequest.setLastHandleStationCode(request.getLastHandleStationCode());
            payRequest.setLastHandleDateTime(request.getLastHandleDateTime());
            payRequest.setTicketTransSeq(request.getTicketTransSeq());
            payRequest.setReserve1(request.getReserve1());
            payRequest.setReserve2(request.getReserve2());
            // 透传 ticket-server 解析出的支付渠道和签约流水号（applyActualCardType 已设置）
            if (StringUtils.hasText(request.getPaymentVendor())) {
                payRequest.setPaymentVendor(request.getPaymentVendor());
            }
            if (StringUtils.hasText(request.getRequestSignSeq())) {
                payRequest.setRequestSignSeq(request.getRequestSignSeq());
            }
            // 透传 ticket-server 返回的票卡状态、订单异常类型、离线码标识、日票相关字段
            if (ticketResponse != null) {
                payRequest.setTicketStatus(ticketResponse.getTicketStatus());
                payRequest.setOrderExpType(ticketResponse.getOrderExpType());
                payRequest.setOfflineFlag(ticketResponse.getOfflineFlag());
                payRequest.setTicketCode(ticketResponse.getTicketCode());
                payRequest.setCountingTimes(ticketResponse.getCountingTimes());
                payRequest.setCountingFlag(ticketResponse.getCountingFlag());
                payRequest.setAttributableParty(ticketResponse.getAttributableParty());
                payRequest.setReceivingParty(ticketResponse.getReceivingParty());
                payRequest.setPayChannelCode(ticketResponse.getPayChannelCode());
                log.info("IF1A-01 透传 ticketResponse 字段到 payRequest, cardId={}, ticketStatus={}, orderExpType={}, offlineFlag={}, companionFlag={}, ticketCode={}, countingTimes={}, countingFlag={}, attributableParty={}, receivingParty={}, payChannelCode={}, discountFee={}, discountInfo={}",
                        request.getCardId(), ticketResponse.getTicketStatus(),
                        ticketResponse.getOrderExpType(), ticketResponse.getOfflineFlag(),
                        ticketResponse.getCompanionFlag(), ticketResponse.getTicketCode(),
                        ticketResponse.getCountingTimes(), ticketResponse.getCountingFlag(),
                        ticketResponse.getAttributableParty(), ticketResponse.getReceivingParty(),
                        ticketResponse.getPayChannelCode(), ticketResponse.getDiscountFee(), ticketResponse.getDiscountInfo());
            } else {
                log.warn("IF1A-01 ticketResponse 为null，ticketStatus/orderExpType/offlineFlag 将为null, cardId={}", request.getCardId());
            }
            // 补充进出站中文站名（由 para-server 查询）
            fillStationNames(payRequest, request.getLastHandleStationCode(), request.getHandleStationCode());
            log.info("IF1A-01 扣费请求关键字段, cardId={}, ticketStatus={}, orderExpType={}, offlineFlag={}, entryStationName={}, exitStationName={}",
                    request.getCardId(), payRequest.getTicketStatus(), payRequest.getOrderExpType(),
                    payRequest.getOfflineFlag(), payRequest.getEntryStationName(), payRequest.getExitStationName());
            log.info("IF1A-01 出站交易调用扣费交易服务, cardId={}, trxType={}", request.getCardId(), request.getTrxType());
            GateTxnPayRespDTO payResponse = gateTxnPayClient.requestGateTxnPay(payRequest);
            log.info("IF1A-01 出站交易调用扣费交易服务, retCode={}", payResponse != null ? payResponse.getRetCode() : "null");
        } catch (Exception e) {
            log.error("IF1A-01 出站交易调用扣费交易服务异常, cardId={}, trxType={}, handleDateTime={}",
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime(), e);
        }
    }

    /**
     * 根据进出站编码查询中文站名并写入扣费请求。
     */
    private void fillStationNames(GateTxnPayReqDTO payRequest, String entryStationCode, String exitStationCode) {
        if (!StringUtils.hasText(entryStationCode)) {
            return;
        }
        try {
            RequestStationLineInfoResult entryInfo = queryStationLineInfo(entryStationCode);
            if (entryInfo != null && StringUtils.hasText(entryInfo.getStationName())) {
                payRequest.setEntryStationName(entryInfo.getStationName());
            }
        } catch (Exception e) {
            log.warn("查询进站站名失败, stationCode={}", entryStationCode, e);
        }
        try {
            RequestStationLineInfoResult exitInfo = queryStationLineInfo(exitStationCode);
            if (exitInfo != null && StringUtils.hasText(exitInfo.getStationName())) {
                payRequest.setExitStationName(exitInfo.getStationName());
            }
        } catch (Exception e) {
            log.warn("查询出站站名失败, stationCode={}", exitStationCode, e);
        }
    }
}
