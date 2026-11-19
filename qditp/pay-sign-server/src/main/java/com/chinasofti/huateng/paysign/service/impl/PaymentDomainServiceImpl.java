package com.chinasofti.huateng.paysign.service.impl;

import static com.chinasofti.huateng.paysign.support.PayTxnRules.buildPayCallbackLog;
import static com.chinasofti.huateng.paysign.support.PayTxnRules.resolveDebitRequestResult;
import static com.chinasofti.huateng.paysign.support.PayTxnRules.validatePaySignInfo;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateRequestPay;
import static com.chinasofti.huateng.paysign.support.PaySignValues.convertPayStatus;
import static com.chinasofti.huateng.paysign.support.PaySignValues.normalizeVendor;
import static com.chinasofti.huateng.paysign.support.PaySignValues.resolveTxnDate;
import static com.chinasofti.huateng.paysign.support.PaySignValues.stringValue;
import static com.chinasofti.huateng.paysign.support.PaymentChannels.isWallet;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PayCallbackLog;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.mapper.PayCallbackLogMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.model.paysign.RegisterCompletedPayTxnReqDTO;
import com.chinasofti.huateng.paysign.service.PaymentDomainService;
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.paysign.port.GatewayReply;
import com.chinasofti.huateng.paysign.port.DebitSyncPort;
import com.chinasofti.huateng.paysign.port.PaymentGatewayPort;
import com.chinasofti.huateng.paysign.port.PaymentReply;
import com.chinasofti.huateng.paysign.port.AccountQuery;
import com.chinasofti.huateng.paysign.port.AccountUserView;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.paysign.domain.PaySignDuplicateKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PaymentDomainServiceImpl implements PaymentDomainService {
    private static final Logger log = LoggerFactory.getLogger(PaymentDomainServiceImpl.class);



    private static final Set<String> GATEWAY_PAY_SCENES = Set.of("scan", "app", "withholding", "wap", "qrcode");

    private final PaySignProperties paySignProperties;
    private final PayTxnDetailMapper payTxnDetailMapper;
    private final PayCallbackLogMapper payCallbackLogMapper;
    private final AccountDomainPort accountDomainPort;
    private final BlacklistClient blacklistClient;
    private final DebitSyncPort debitSyncPort;
    private final PaymentGatewayPort paymentGatewayPort;

    public PaymentDomainServiceImpl(
            PaySignProperties paySignProperties,
            PayTxnDetailMapper payTxnDetailMapper,
            PayCallbackLogMapper payCallbackLogMapper,
            AccountDomainPort accountDomainPort,
            BlacklistClient blacklistClient,
            DebitSyncPort debitSyncPort,
            PaymentGatewayPort paymentGatewayPort) {
        this.paySignProperties = paySignProperties;
        this.payTxnDetailMapper = payTxnDetailMapper;
        this.payCallbackLogMapper = payCallbackLogMapper;
        this.accountDomainPort = accountDomainPort;
        this.blacklistClient = blacklistClient;
        this.debitSyncPort = debitSyncPort;
        this.paymentGatewayPort = paymentGatewayPort;
    }

    @Override
    public RequestPayResult requestPay(RequestPayReqDTO request) {
        RequestPayResult response = new RequestPayResult();
        try {
            PayTxnDetail existingTxn = null;
            if (request != null && StringUtils.hasText(request.getOrderNo())) {
                existingTxn = payTxnDetailMapper.selectByOrderNo(request.getOrderNo());
                if (existingTxn != null) {
                    if (!StringUtils.hasText(request.getPaymentVendor())) {
                        request.setPaymentVendor(existingTxn.getPaymentVendor());
                    }
                    if (!StringUtils.hasText(request.getRequestSignSeq())) {
                        request.setRequestSignSeq(existingTxn.getRequestSignSeq());
                    }
                    if (!StringUtils.hasText(request.getPayUserId())) {
                        request.setPayUserId(existingTxn.getPayUserId());
                    }
                } else if (!StringUtils.hasText(request.getPaymentVendor())
                        || !StringUtils.hasText(request.getRequestSignSeq())) {
                    resolvePaySignInfoFromAccount(request);
                }
            }
            String validMsg = validateRequestPay(request);

            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                log.info("REQUEST_PAY 参数校验失败, request={}, response={}", JSON.toJSONString(request), JSON.toJSONString(response));
                return response;
            }

            if (!validatePaySignInfo(request, response)) {
                log.info("REQUEST_PAY 签约信息校验失败, request={}, response={}",
                        JSON.toJSONString(request), JSON.toJSONString(response));
                return response;
            }

            warnIfSceneOutsideGatewayEnum(request);
            ensurePayTxn(request, existingTxn);
            payTxnDetailMapper.markRequesting(request.getOrderNo());
            log.info("REQUEST_PAY ensurePayTxn完成, orderNo={}, paymentVendor={}, discountFee={}, discountInfo={}",
                    request.getOrderNo(), request.getPaymentVendor(), request.getDiscountFee(), request.getDiscountInfo());

            PaymentReply reply = paymentGatewayPort.requestPay(request);
            PaySignGatewayResponse gatewayResponse = reply.raw();

            if (reply instanceof PaymentReply.AlreadyPaid) {
                log.info("REQUEST_PAY 订单已支付成功（幂等），orderNo={}", request.getOrderNo());
                fillSuccess(response);
                fillGatewayFields(response, reply);
                updatePayRequestResult(request.getOrderNo(), "SUCCESS", response, null);
                return response;
            }
            if (reply instanceof PaymentReply.Rejected rejected) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, rejected.messageOr("请求支付接口失败"));
                fillGatewayFields(response, reply);
                String transIn = reply.data() != null ? stringValue(reply.data().get("transIn"), null) : null;
                updatePayRequestResult(request.getOrderNo(), "RETRY", response, transIn);

                String paymentVendor = request.getPaymentVendor();
                if (StringUtils.hasText(paymentVendor)
                        && ("03".equals(paymentVendor) || "05".equals(paymentVendor))) {
                    addBlacklistForPaymentFailure(request, gatewayResponse);
                }

                return response;
            }
            fillSuccess(response);
            fillGatewayFields(response, reply);
            String transIn = gatewayResponse.getData() != null ? stringValue(gatewayResponse.getData().get("transIn"), null) : null;
            if (gatewayResponse.getData() != null) {
                response.setOrderNo(stringValue(gatewayResponse.getData().get("merchantOrderNo"), request.getOrderNo()));
                response.setMerchantOrderNo(stringValue(gatewayResponse.getData().get("orderNo"), null));
                response.setChannelOrderNo(stringValue(gatewayResponse.getData().get("channelOrderNo"), null));
                response.setPayData(stringValue(gatewayResponse.getData().get("data"), null));
            } else {
                response.setOrderNo(request.getOrderNo());
            }
            updatePayRequestResult(request.getOrderNo(), "PROCESSING", response, transIn);
            return response;
        } catch (Exception e) {
            log.error("处理请求支付异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            if (request != null && StringUtils.hasText(request.getOrderNo())) {
                try {
                    PayTxnDetail current = payTxnDetailMapper.selectByOrderNo(request.getOrderNo());
                    if (current == null || !"PROCESSING".equals(current.getPayStatus())) {
                        updatePayRequestResult(request.getOrderNo(), "RETRY", response, null);
                    } else {
                        log.warn("支付请求异常但订单已处于PROCESSING状态，跳过状态回退, orderNo={}", request.getOrderNo());
                    }
                } catch (Exception ex) {
                    log.error("查询或更新PAY_TXN_DETAIL异常, orderNo={}, 订单需人工补偿", request.getOrderNo(), ex);
                }
            }
            return response;
        }
    }

    private static final String CALLBACK_TYPE_PAY = "PAY";

    private static final int MAX_PAY_CALLBACK_PUSH = 2;

    private static final int PAY_QUERY_SCAN_DAYS = 7;

    private static final int PAY_QUERY_STALE_MINUTES = 10;

    private static final int PAY_QUERY_BATCH_SIZE = 20;

    private static final long PAY_QUERY_BUDGET_MILLIS = 45_000L;

    private static final int PAY_QUERY_MANUAL_ALERT_DAYS = 2;

    @Override
    public PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request, String rawBody) {
        PaySignCallbackResult response = new PaySignCallbackResult();
        int pushCount = 0;
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "回调报文不能为空");
                return response;
            }
            if (!StringUtils.hasText(request.getMerchantOrderNo()) && !StringUtils.hasText(request.getOrderNo())) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "merchantOrderNo不能为空");
                return response;
            }
            PayCallbackLog callbackLog = buildPayCallbackLog(request, rawBody);
            payCallbackLogMapper.insert(callbackLog);

            if (StringUtils.hasText(request.getMerchantOrderNo())) {
                pushCount = payCallbackLogMapper.countByMerchantOrderNo(
                        request.getMerchantOrderNo(), CALLBACK_TYPE_PAY);
            }

            if (!StringUtils.hasText(request.getMerchantOrderNo())) {
                log.error("支付结果回调缺少 merchantOrderNo，无法定位订单，MUST 人工核对, 支付中心orderNo={}, channelOrderNo={}, status={}",
                        request.getOrderNo(), request.getChannelOrderNo(), request.getStatus());
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "merchantOrderNo不能为空");
                return response;
            }

            PayTxnDetail update = new PayTxnDetail();
            update.setOrderNo(request.getMerchantOrderNo());
            update.setPayStatus(convertPayStatus(request.getStatus()));
            update.setDebitRequestResult(resolveDebitRequestResult(update.getPayStatus()));
            update.setMerchantOrderNo(request.getMerchantOrderNo());
            update.setPayCenterOrderNo(request.getOrderNo());
            update.setChannelOrderNo(request.getChannelOrderNo());
            update.setTotalAmount(request.getTotalAmount());
            update.setCashAmount(request.getCashAmount());
            update.setCouponAmount(request.getCouponAmount());
            update.setPayUserId(request.getPayUserId());
            update.setPaymentVendor(request.getPaymentVendor());
            update.setPayTime(request.getPayTime());
            int affected = payTxnDetailMapper.updatePayCallback(update);
            if (affected == 0) {
                String currentPayStatus = queryPayStatus(request.getMerchantOrderNo());
                if (!update.getPayStatus().equals(currentPayStatus)) {
                    log.error("支付结果回调未命中任何订单，MUST 人工核对支付中心与本地口径, merchantOrderNo={}, 支付中心orderNo={}, channelOrderNo={}, status={}, payTime={}, 本地PAY_STATUS={}",
                            request.getMerchantOrderNo(), request.getOrderNo(), request.getChannelOrderNo(),
                            request.getStatus(), request.getPayTime(), currentPayStatus);
                    if (shouldStopRetry(pushCount)) {
                        giveUpRetry(response, request.getMerchantOrderNo(), pushCount, "支付订单不存在或状态不允许更新");
                        return response;
                    }
                    fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "支付订单不存在或状态不允许更新");
                    return response;
                }
                log.info("支付结果回调重复推送，本地已是目标状态，跳过回写继续收敛扣费订单, merchantOrderNo={}, payStatus={}, pushCount={}",
                        request.getMerchantOrderNo(), currentPayStatus, pushCount);
            }

            if (!syncGateTxnPayStatus(request.getMerchantOrderNo(), update.getPayStatus())) {
                if (shouldStopRetry(pushCount)) {
                    giveUpRetry(response, request.getMerchantOrderNo(), pushCount, "扣费订单状态同步失败");
                    return response;
                }
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "扣费订单状态同步失败，待支付中心重推");
                return response;
            }

            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("处理支付结果回调异常, request={}", JSON.toJSONString(request), e);
            if (request != null && shouldStopRetry(pushCount)) {
                giveUpRetry(response, request.getMerchantOrderNo(), pushCount, "处理支付结果回调异常：" + e.getMessage());
                return response;
            }
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    public CompensateNotifyRespDTO compensatePayStatus() {
        CompensateNotifyRespDTO response = new CompensateNotifyRespDTO();
        try {
            String txnDateFrom = LocalDate.now().minusDays(PAY_QUERY_SCAN_DAYS)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            List<PayTxnDetail> pending = payTxnDetailMapper.selectCompensablePayStatus(
                    txnDateFrom, PAY_QUERY_STALE_MINUTES, PAY_QUERY_BATCH_SIZE);
            if (pending == null || pending.isEmpty()) {
                log.info("支付状态回查：无静默超过{}分钟的非终态单, txnDateFrom={}",
                        PAY_QUERY_STALE_MINUTES, txnDateFrom);
                alertManualCallbacks();
                fillSuccess(response);
                return response;
            }
            long deadline = System.nanoTime() + PAY_QUERY_BUDGET_MILLIS * 1_000_000L;
            int settled = 0;
            int skipped = 0;
            int index = 0;
            for (; index < pending.size(); index++) {
                if (System.nanoTime() > deadline) {
                    log.warn("支付状态回查时间预算{}ms用尽，剩余{}笔留待下一轮（cron 每5分钟、禁止并发）",
                            PAY_QUERY_BUDGET_MILLIS, pending.size() - index);
                    break;
                }
                PayTxnDetail row = pending.get(index);
                try {
                    if (settlePayByQuery(row)) {
                        settled++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    skipped++;
                    log.error("单条支付状态回查异常，NEVER 因此中断整批, orderNo={}", row.getOrderNo(), e);
                }
            }
            int untouched = pending.size() - index;
            response.setScanned(pending.size());
            response.setSubmitted(settled);
            response.setSkipped(skipped + untouched);
            log.info("支付状态回查补偿完成, txnDateFrom={}, scanned={}, settled={}, pendingAgain={}, 预算外未处理={}",
                    txnDateFrom, pending.size(), settled, skipped, untouched);
            alertManualCallbacks();
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("支付状态回查补偿异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }


    private boolean settlePayByQuery(PayTxnDetail row) {
        GatewayReply reply = paymentGatewayPort.queryPayStatus(row.getOrderNo());
        String resolvedStatus = resolveQueryPayStatus(reply);
        log.info("支付状态回查支付中心返回, orderNo={}, 本地PAY_STATUS={}, 解析结果={}, gatewayResponse={}",
                row.getOrderNo(), row.getPayStatus(), resolvedStatus, JSON.toJSONString(reply.raw()));
        if (resolvedStatus == null) {
            return false;
        }
        Map<String, Object> data = reply instanceof GatewayReply.Accepted accepted ? accepted.data() : null;
        PayTxnDetail update = new PayTxnDetail();
        update.setOrderNo(row.getOrderNo());
        update.setPayStatus(resolvedStatus);
        update.setDebitRequestResult(resolveDebitRequestResult(resolvedStatus));
        update.setMerchantOrderNo(row.getOrderNo());
        if (data != null) {
            update.setPayCenterOrderNo(stringValue(data.get("orderNo"), null));
            update.setChannelOrderNo(stringValue(data.get("channelOrderNo"), null));
            update.setTotalAmount(intValue(data.get("totalAmount")));
            update.setCashAmount(intValue(data.get("cashAmount")));
            update.setCouponAmount(intValue(data.get("couponAmount")));
            update.setPaymentVendor(stringValue(data.get("paymentVendor"), null));
            update.setPayTime(stringValue(data.get("payDate"), null));
        }
        int affected = payTxnDetailMapper.updatePayCallback(update);
        if (affected == 0) {
            log.info("支付状态回查收敛命中 0 行，说明已被回调或其他路径收口, orderNo={}, 目标状态={}",
                    row.getOrderNo(), resolvedStatus);
            return true;
        }
        return syncGateTxnPayStatus(row.getOrderNo(), resolvedStatus);
    }


    private String resolveQueryPayStatus(GatewayReply reply) {
        if (reply instanceof GatewayReply.Accepted accepted) {
            Map<String, Object> data = accepted.data();
            if (data == null) {
                return null;
            }
            String mapped = convertPayStatus(stringValue(data.get("status"), null));
            return "SUCCESS".equals(mapped) || "FAIL".equals(mapped) ? mapped : null;
        }
        if (reply instanceof GatewayReply.Rejected rejected) {
            // payQuery 的「中心无此单」实测有两种文案：「查无此交易[2600000]」与「未找到数据」，
            // 都带业务码 9999。NEVER 只按文案匹配——文案由供方定、随时会变。
            // 误判为 FAIL 也可自愈：成功回调按方向敏感白名单能把 FAIL 收敛回 SUCCESS。
            PaySignGatewayResponse raw = rejected.raw();
            String msg = rejected.messageOr("");
            boolean notFound = (raw != null && Integer.valueOf(9999).equals(raw.getCode()))
                    || msg.contains("查无此交易") || msg.contains("未找到") || msg.contains("不存在");
            if (notFound) {
                log.info("支付中心查无此单，按失败收敛, msg={}", msg);
                return "FAIL";
            }
        }
        return null;
    }


    private void alertManualCallbacks() {
        try {
            String txnDateFrom = LocalDate.now().minusDays(PAY_QUERY_MANUAL_ALERT_DAYS)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            int manual = payCallbackLogMapper.countManualSince(txnDateFrom);
            if (manual > 0) {
                log.error("存在 {} 条 HANDLE_STATUS=MANUAL 的支付回调（近{}天），MUST 人工重放："
                                + "POST /internal/payment/replayCallback?merchantOrderNo=<单号>",
                        manual, PAY_QUERY_MANUAL_ALERT_DAYS);
            }
        } catch (Exception e) {
            log.warn("MANUAL 回调巡检失败，不影响本轮收口", e);
        }
    }


    @Override
    public PaySignCallbackResult replayCallback(String merchantOrderNo) {
        PaySignCallbackResult response = new PaySignCallbackResult();
        if (!StringUtils.hasText(merchantOrderNo)) {
            fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "merchantOrderNo不能为空");
            return response;
        }
        PayCallbackLog latest = payCallbackLogMapper.selectLatestByMerchantOrderNo(
                merchantOrderNo.trim(), CALLBACK_TYPE_PAY);
        if (latest == null) {
            fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "无该商户单号的支付回调凭据，无法重放");
            return response;
        }
        ReceivePayResultReqDTO dto = parseCallbackDto(latest.getRawBody());
        if (dto == null || !StringUtils.hasText(dto.getMerchantOrderNo())) {
            fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "回调凭据报文无法还原为支付结果，需人工核对");
            return response;
        }
        log.warn("人工重放支付回调, merchantOrderNo={}, 最近回调状态={}, 回调时间={}, 原HANDLE_STATUS={}",
                merchantOrderNo, latest.getCallbackStatus(), latest.getCreateTime(), latest.getHandleStatus());
        return receivePayResult(dto, latest.getRawBody());
    }


    private ReceivePayResultReqDTO parseCallbackDto(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(rawBody);
            if (root == null) {
                return null;
            }
            if (root.containsKey("bizData")) {
                Object bizData = root.get("bizData");
                if (bizData instanceof JSONObject biz) {
                    return biz.toJavaObject(ReceivePayResultReqDTO.class);
                }
                if (bizData instanceof String text && StringUtils.hasText(text)) {
                    return JSON.parseObject(decodeIfBase64(text), ReceivePayResultReqDTO.class);
                }
            }
            return root.toJavaObject(ReceivePayResultReqDTO.class);
        } catch (Exception e) {
            log.error("解析回调凭据报文失败, rawBody={}", rawBody, e);
            return null;
        }
    }


    private String decodeIfBase64(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        try {
            return new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return trimmed;
        }
    }


    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }


    private String queryPayStatus(String orderNo) {
        try {
            PayTxnDetail existing = payTxnDetailMapper.selectByOrderNo(orderNo);
            return existing == null ? null : existing.getPayStatus();
        } catch (Exception e) {
            log.error("查询支付订单当前状态失败, orderNo={}", orderNo, e);
            return null;
        }
    }

    private boolean shouldStopRetry(int pushCount) {
        return pushCount >= MAX_PAY_CALLBACK_PUSH;
    }

    private void giveUpRetry(PaySignCallbackResult response, String merchantOrderNo, int pushCount, String reason) {
        log.error("支付结果回调已推送{}次仍未处理成功，达到上限{}，放弃重推并返回0000，MUST 人工处理, merchantOrderNo={}, 原因={}",
                pushCount, MAX_PAY_CALLBACK_PUSH, merchantOrderNo, reason);
        try {
            payCallbackLogMapper.markManualByMerchantOrderNo(merchantOrderNo, CALLBACK_TYPE_PAY,
                    reason == null ? null : reason.substring(0, Math.min(reason.length(), 300)));
        } catch (Exception e) {
            log.error("标记回调需人工处理失败, merchantOrderNo={}", merchantOrderNo, e);
        }
        fillSuccess(response);
    }


    private void warnIfSceneOutsideGatewayEnum(RequestPayReqDTO request) {
        String scene = request.getScene();
        if (!GATEWAY_PAY_SCENES.contains(scene)) {
            log.warn("REQUEST_PAY scene 不在支付中心网关枚举内（仅告警不拒绝）, orderNo={}, scene={}, 网关枚举={}",
                    request.getOrderNo(), scene, GATEWAY_PAY_SCENES);
        }
    }


    private void ensurePayTxn(RequestPayReqDTO request, PayTxnDetail existingTxn) {
        if (existingTxn != null) {
            log.info("ensurePayTxn 订单已存在，跳过插入, orderNo={}", request.getOrderNo());
            return;
        }
        PayTxnDetail record = new PayTxnDetail();
        record.setOrderNo(request.getOrderNo());
        record.setPayType("PAY");
        record.setPayStatus("INIT");
        record.setThirdUserId(request.getThirdUserId());
        record.setCardId(request.getCardId());
        record.setCardType(request.getCardType());
        record.setPaymentVendor(request.getPaymentVendor());
        record.setRequestSignSeq(request.getRequestSignSeq());
        record.setPayUserId(request.getPayUserId());
        record.setAmount(request.getAmount());
        record.setRefundStatus("NONE");
        record.setRefundAmount(0);
        record.setRequestCount(0);
        record.setTxnDate(resolveTxnDate(request.getTxnDate()));
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDiscountInfo(request.getDiscountInfo());
        record.setDiscountFee(request.getDiscountFee());
        try {
            payTxnDetailMapper.insert(record);
        } catch (RuntimeException e) {
            if (!PaySignDuplicateKey.isConflict(e)) { throw e; }
            log.warn("ensurePayTxn 并发插入重复，orderNo={}, msg={}", request.getOrderNo(), e.getMessage());
        }
    }


    @Override
    public BaseRespDTO registerCompletedTxn(RegisterCompletedPayTxnReqDTO request) {
        BaseRespDTO response = new BaseRespDTO();
        String validMsg = validateRegisterCompletedTxn(request);
        if (validMsg != null) {
            response.setRetCode(PaySignErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg(validMsg);
            log.warn("REGISTER_COMPLETED_TXN 参数校验失败, request={}, msg={}", request, validMsg);
            return response;
        }
        PayTxnDetail existing = payTxnDetailMapper.selectByOrderNo(request.getOrderNo());
        if (existing != null) {
            response.setRetCode("0000");
            response.setRetMsg("成功");
            log.info("REGISTER_COMPLETED_TXN 流水已存在，按幂等返回成功, orderNo={}, payStatus={}",
                    request.getOrderNo(), existing.getPayStatus());
            return response;
        }
        LocalDateTime now = LocalDateTime.now();
        PayTxnDetail record = new PayTxnDetail();
        record.setOrderNo(request.getOrderNo());
        record.setPayType("PAY");
        record.setPayStatus("SUCCESS");
        record.setDebitRequestResult("SUCCESS");
        record.setThirdUserId(request.getThirdUserId());
        record.setCardId(request.getCardId());
        record.setCardType(request.getCardType());
        record.setPaymentVendor(request.getPaymentVendor());
        record.setRequestSignSeq(request.getRequestSignSeq());
        record.setPayUserId(request.getPayUserId());
        record.setAmount(request.getAmount());
        record.setTotalAmount(request.getAmount());
        record.setMerchantOrderNo(request.getOrderNo());
        record.setRefundStatus("NONE");
        record.setRefundAmount(0);
        record.setRequestCount(0);
        record.setPayTime(DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now));
        record.setResponseTime(now);
        record.setTxnDate(resolveTxnDate(request.getTxnDate()));
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            payTxnDetailMapper.insert(record);
        } catch (RuntimeException e) {
            if (!PaySignDuplicateKey.isConflict(e)) { throw e; }
            log.warn("REGISTER_COMPLETED_TXN 并发插入重复，按幂等返回成功, orderNo={}, msg={}",
                    request.getOrderNo(), e.getMessage());
        }
        response.setRetCode("0000");
        response.setRetMsg("成功");
        log.info("REGISTER_COMPLETED_TXN 已登记不经支付中心的支付流水, orderNo={}, amount={}, txnDate={}, reason={}",
                request.getOrderNo(), request.getAmount(), record.getTxnDate(), request.getReason());
        return response;
    }


    private String validateRegisterCompletedTxn(RegisterCompletedPayTxnReqDTO request) {
        if (request == null) {
            return "请求体不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        if (request.getAmount() == null) {
            return "amount不能为空";
        }
        if (request.getAmount() < 0) {
            return "amount不能为负数: " + request.getAmount();
        }
        return null;
    }

    private void resolvePaySignInfoFromAccount(RequestPayReqDTO request) {
        if (!StringUtils.hasText(request.getCardId()) || !StringUtils.hasText(request.getThirdUserId())) {
            log.warn("resolvePaySignInfoFromAccount: cardId或thirdUserId为空，跳过, orderNo={}", request.getOrderNo());
            return;
        }
        try {
            switch (accountDomainPort.queryUser(
                    request.getThirdUserId(), request.getCardId(), request.getCardType())) {
                case AccountQuery.Found<AccountUserView> found -> {
                    applyAccountUserView(request, found.value());
                    log.info("resolvePaySignInfoFromAccount: 从account-server补充签约信息, orderNo={}, paymentVendor={}, requestSignSeq={}",
                            request.getOrderNo(), request.getPaymentVendor(), request.getRequestSignSeq());
                }
                case AccountQuery.NotFound<AccountUserView> notFound ->
                        log.warn("resolvePaySignInfoFromAccount: account-server 未答成功，不采信本次应答, orderNo={}, cardId={}, retCode={}, retMsg={}",
                                request.getOrderNo(), request.getCardId(), notFound.retCode(), notFound.retMsg());
                case AccountQuery.Unreachable<AccountUserView> unreachable ->
                        log.error("resolvePaySignInfoFromAccount: 未获 account-server 业务答复, orderNo={}",
                                request.getOrderNo(), unreachable.cause());
            }
        } catch (Exception e) {
            log.error("resolvePaySignInfoFromAccount: 补签约信息异常, orderNo={}", request.getOrderNo(), e);
        }
    }

    private void applyAccountUserView(RequestPayReqDTO request, AccountUserView view) {
        if (view.channel() != null) {
            request.setPaymentVendor(view.channel());
        } else {
            log.warn("resolvePaySignInfoFromAccount: account-server未返回channel, cardId={}", request.getCardId());
        }
        boolean wallet = isWallet(request.getPaymentVendor());
        if (wallet) {
            if (view.thirdPayId() != null) {
                request.setPayUserId(view.thirdPayId());
            } else {
                log.warn("resolvePaySignInfoFromAccount: 钱包用户未返回thirdPayId, cardId={}", request.getCardId());
            }
        }
        if (view.reqContractNo() != null) {
            request.setRequestSignSeq(view.reqContractNo());
        } else {
            log.warn("resolvePaySignInfoFromAccount: account-server未返回reqContractNo, cardId={}, wallet={}",
                    request.getCardId(), wallet);
        }
    }

    private void updatePayRequestResult(String orderNo, String payStatus, RequestPayResult response, String transIn) {
        if (!StringUtils.hasText(orderNo)) {
            log.error("updatePayRequestResult: orderNo 为空，跳过更新, payStatus={}", payStatus);
            return;
        }
        PayTxnDetail record = new PayTxnDetail();
        record.setOrderNo(orderNo);
        record.setPayStatus(payStatus);
        record.setMerchantOrderNo(response.getOrderNo());
        record.setPayCenterOrderNo(response.getMerchantOrderNo());
        record.setChannelOrderNo(response.getChannelOrderNo());
        record.setDebitRequestResult(resolveDebitRequestResult(payStatus));
        record.setResponseTime(LocalDateTime.now());
        record.setTransIn(transIn);
        payTxnDetailMapper.updateRequestResult(record);
    }


    private boolean syncGateTxnPayStatus(String orderNo, String payStatus) {
        return switch (debitSyncPort.syncDebitStatus(orderNo, payStatus, "支付结果回调")) {
            case RpcOutcome.Ok ignored -> {
                log.info("扣费订单状态同步成功, orderNo={}, payStatus={}", orderNo, payStatus);
                yield true;
            }
            case RpcOutcome.BizRejected rejected -> {
                log.error("扣费订单状态同步被闸机域拒绝，待支付中心重推, orderNo={}, payStatus={}, retCode={}, retMsg={}",
                        orderNo, payStatus, rejected.retCode(), rejected.retMsg());
                yield false;
            }
            case RpcOutcome.Unreachable unreachable -> {
                log.error("扣费订单状态同步未获答复，待支付中心重推, orderNo={}, payStatus={}",
                        orderNo, payStatus, unreachable.cause());
                yield false;
            }
        };
    }

    private void fillGatewayFields(RequestPayResult response, PaymentReply reply) {
        PaySignGatewayResponse gatewayResponse = reply == null ? null : reply.raw();
        if (gatewayResponse == null) {
            return;
        }
        response.setCode(gatewayResponse.getCode());
        response.setMsg(gatewayResponse.getMsg());
        response.setSuccess(reply.successful());
        response.setData(gatewayResponse.getData());
    }

    private void addBlacklistForPaymentFailure(RequestPayReqDTO request, PaySignGatewayResponse gatewayResponse) {
        try {
            String confirmedStatus = queryGatewayPayStatus(request.getOrderNo());
            if (!"FAIL".equals(confirmedStatus)) {
                log.warn("支付调用失败但支付中心未确认为失败，跳过拉黑, orderNo={}, cardId={}, paymentVendor={}, 支付中心状态={}, 本次网关code={}, msg={}",
                        request.getOrderNo(), request.getCardId(), request.getPaymentVendor(), confirmedStatus,
                        gatewayResponse != null ? gatewayResponse.getCode() : "null",
                        gatewayResponse != null ? gatewayResponse.getMsg() : "null");
                return;
            }

            AddBlackListReqDTO blacklistRequest = new AddBlackListReqDTO();
            blacklistRequest.setCardId(request.getCardId());
            blacklistRequest.setCardType(request.getCardType());
            blacklistRequest.setThirdUserId(request.getThirdUserId());
            blacklistRequest.setChannelCode("99");
            blacklistRequest.setBlackSource("01");
            blacklistRequest.setBlackCause("01");
            blacklistRequest.setBizNo(request.getOrderNo());
            blacklistRequest.setCreateBy("pay-sign-server");

            String gatewayMsg = gatewayResponse != null ? gatewayResponse.getMsg() : null;
            if (StringUtils.hasText(gatewayMsg)) {
                blacklistRequest.setReason(gatewayMsg);
            } else {
                blacklistRequest.setReason("支付中心返回非200, code=" + (gatewayResponse != null ? gatewayResponse.getCode() : "null"));
            }

            log.warn("支付失败，准备添加黑名单, orderNo={}, cardId={}, paymentVendor={}, gatewayCode={}, gatewayMsg={}",
                    request.getOrderNo(), request.getCardId(), request.getPaymentVendor(),
                    gatewayResponse != null ? gatewayResponse.getCode() : "null", gatewayMsg);

            BlackListOperateResult blacklistResult = blacklistClient.addBlackList(blacklistRequest);
            log.info("支付失败添加黑名单结果, cardId={}, retCode={}, retMsg={}",
                    request.getCardId(),
                    blacklistResult != null ? blacklistResult.getRetCode() : "null",
                    blacklistResult != null ? blacklistResult.getRetMsg() : "null");
        } catch (Exception e) {
            log.error("支付失败添加黑名单异常, orderNo={}, cardId={}", request.getOrderNo(), request.getCardId(), e);
        }
    }

    private String queryGatewayPayStatus(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        try {
            GatewayReply reply = paymentGatewayPort.queryPayStatus(orderNo);
            if (!(reply instanceof GatewayReply.Accepted accepted) || accepted.data() == null) {
                return null;
            }
            String status = stringValue(accepted.data().get("status"), null);
            if (!StringUtils.hasText(status)) {
                return null;
            }
            return convertPayStatus(status);
        } catch (Exception e) {
            log.error("查询支付中心支付状态异常，按不拉黑处理, orderNo={}", orderNo, e);
            return null;
        }
    }
}
