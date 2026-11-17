package com.chinasofti.huateng.paysign.service.impl;

import static com.chinasofti.huateng.paysign.support.PayRefundRules.buildPayRefundDetail;
import static com.chinasofti.huateng.paysign.support.PayRefundRules.fillRefundResponseFields;
import static com.chinasofti.huateng.paysign.support.PayRefundRules.validateRefundPayTxn;
import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildRequestRefundBizData;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateRequestRefund;
import static com.chinasofti.huateng.paysign.support.PaySignValues.convertPayStatus;
import static com.chinasofti.huateng.paysign.support.PaySignValues.defaultString;
import static com.chinasofti.huateng.paysign.support.PaySignValues.resolveTxnDate;
import static com.chinasofti.huateng.paysign.support.PaySignValues.stringValue;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveRefundResultReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.domain.OutboxScan;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.port.GatewayReply;
import com.chinasofti.huateng.paysign.port.RefundGatewayPort;
import com.chinasofti.huateng.paysign.service.RefundDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RefundDomainServiceImpl implements RefundDomainService {
    private static final Logger log = LoggerFactory.getLogger(RefundDomainServiceImpl.class);

    private final PayTxnDetailMapper payTxnDetailMapper;
    private final PayRefundDetailMapper payRefundDetailMapper;
    private final RefundGatewayPort refundGatewayPort;

    public RefundDomainServiceImpl(
            PayTxnDetailMapper payTxnDetailMapper,
            PayRefundDetailMapper payRefundDetailMapper,
            RefundGatewayPort refundGatewayPort) {
        this.payTxnDetailMapper = payTxnDetailMapper;
        this.payRefundDetailMapper = payRefundDetailMapper;
        this.refundGatewayPort = refundGatewayPort;
    }

    @Override
    public RequestRefundResult requestRefund(RequestRefundReqDTO request) {
        RequestRefundResult response = new RequestRefundResult();
        try {
            String validMsg = validateRequestRefund(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                log.info("REQUEST_REFUND 参数校验失败, request={}, response={}", JSON.toJSONString(request), JSON.toJSONString(response));
                return response;
            }

            PayTxnDetail payTxn = payTxnDetailMapper.selectByOrderNo(request.getOrderNo());
            validMsg = validateRefundPayTxn(payTxn, request.getRefundAmount());
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                log.info("REQUEST_REFUND 原支付订单校验失败, request={}, payTxn={}, response={}",
                        JSON.toJSONString(request), JSON.toJSONString(payTxn), JSON.toJSONString(response));
                return response;
            }

            int inFlight = payRefundDetailMapper.countInFlightByOrderNo(payTxn.getOrderNo());
            if (inFlight > 0) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "该订单存在处理中的退款，请先确认上一笔结果");
                log.warn("REQUEST_REFUND 同一原单存在在途退款，拒绝重复提交, orderNo={}, inFlight={}, request={}",
                        payTxn.getOrderNo(), inFlight, JSON.toJSONString(request));
                return response;
            }

            PayRefundDetail refundDetail = buildPayRefundDetail(request, payTxn);
            payRefundDetailMapper.insert(refundDetail);

            String notifyUrl = refundGatewayPort.refundNotifyUrl();
            if (!StringUtils.hasText(notifyUrl)) {
                log.warn("未配置 pay.sign.request-refund-notify-url，本笔退款不送 notifyUrl，"
                                + "支付中心无处推 §5.2 退款回调，只能靠 §3.2 回查收敛, orderNo={}, refundOrderNo={}",
                        refundDetail.getOrderNo(), refundDetail.getRefundOrderNo());
            }
            Map<String, Object> bizData = buildRequestRefundBizData(refundDetail, payTxn, notifyUrl);
            payRefundDetailMapper.markRequesting(refundDetail.getRefundOrderNo(), refundDetail.getTxnDate(), JSON.toJSONString(bizData));
            log.info("REQUEST_REFUND 调用支付平台, orderNo={}, refundOrderNo={}, bizData={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo(), JSON.toJSONString(bizData));
            GatewayReply reply = refundGatewayPort.requestRefund(bizData);
            PaySignGatewayResponse gatewayResponse = reply.raw();
            log.info("REQUEST_REFUND 支付平台返回, orderNo={}, refundOrderNo={}, gatewayResponse={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo(), JSON.toJSONString(gatewayResponse));

            response.setOrderNo(refundDetail.getOrderNo());
            response.setRefundOrderNo(refundDetail.getRefundOrderNo());
            fillGatewayFields(response, reply);

            if (reply instanceof GatewayReply.Rejected rejected) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, rejected.messageOr("请求退款接口失败"));
                fillGatewayFields(response, reply);
                settleRejectedRequestByQuery(refundDetail, payTxn, response, gatewayResponse);
                return response;
            }
            fillSuccess(response);
            fillRefundResponseFields(response, refundDetail, gatewayResponse);
            updateRefundRequestResult(refundDetail, "SUCCESS", response, gatewayResponse);
            int summaryAffected = payTxnDetailMapper.updateRefundSummary(payTxn.getOrderNo());
            if (summaryAffected == 0) {
                log.error("退款汇总回写未命中原支付订单，MUST 人工核对 PAY_TXN_DETAIL 与 PAY_REFUND_DETAIL, orderNo={}, refundOrderNo={}",
                        payTxn.getOrderNo(), refundDetail.getRefundOrderNo());
            }
            return response;
        } catch (Exception e) {
            log.error("处理请求退款异常, request={}", JSON.toJSONString(request), e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }


    private void settleRejectedRequestByQuery(PayRefundDetail refundDetail, PayTxnDetail payTxn,
                                              RequestRefundResult response, PaySignGatewayResponse gatewayResponse) {
        if (!refundGatewayPort.refundQueryConfigured()) {
            log.error("请求退款被支付中心拒但未配置 pay.sign.refund-query-url，无法回查定性，本笔落 PROCESSING 等配置补齐后由退款回查收口, "
                    + "orderNo={}, refundOrderNo={}", refundDetail.getOrderNo(), refundDetail.getRefundOrderNo());
            updateRefundRequestResult(refundDetail, "PROCESSING", response, gatewayResponse);
            return;
        }

        GatewayReply queryReply = null;
        String settledStatus = null;
        try {
            Map<String, Object> bizData = new LinkedHashMap<>();
            bizData.put("refundOrderNo", refundDetail.getRefundOrderNo());
            bizData.put("merchantRefundNo", refundDetail.getRefundOrderNo());
            queryReply = refundGatewayPort.queryRefund(bizData);
            log.info("请求退款被拒后回查支付中心, orderNo={}, refundOrderNo={}, gatewayResponse={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo(), JSON.toJSONString(queryReply.raw()));
            settledStatus = resolveRefundQueryStatus(queryReply);
        } catch (Exception e) {
            log.error("请求退款被拒后回查异常，本笔落 PROCESSING 等退款回查补偿收口, orderNo={}, refundOrderNo={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo(), e);
        }

        if (settledStatus == null) {
            log.warn("请求退款被拒且回查未得终态，本笔落 PROCESSING 等退款回查补偿收口, orderNo={}, refundOrderNo={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo());
            updateRefundRequestResult(refundDetail, "PROCESSING", response, gatewayResponse);
            return;
        }

        PaySignGatewayResponse queryResponse = queryReply.raw();
        if ("FAIL".equals(settledStatus)) {
            log.info("请求退款被拒且回查确认失败，按终态 FAIL 收口, orderNo={}, refundOrderNo={}",
                    refundDetail.getOrderNo(), refundDetail.getRefundOrderNo());
            updateRefundRequestResult(refundDetail, "FAIL", response, queryResponse);
            return;
        }

        fillSuccess(response);
        fillGatewayFields(response, queryReply);
        fillRefundResponseFields(response, refundDetail, queryResponse);
        updateRefundRequestResult(refundDetail, "SUCCESS", response, queryResponse);
        int summaryAffected = payTxnDetailMapper.updateRefundSummary(payTxn.getOrderNo());
        if (summaryAffected == 0) {
            log.error("请求退款被拒但回查已成功，退款汇总回写未命中原支付订单，MUST 人工核对 PAY_TXN_DETAIL 与 PAY_REFUND_DETAIL, "
                    + "orderNo={}, refundOrderNo={}", payTxn.getOrderNo(), refundDetail.getRefundOrderNo());
        }
        log.info("请求退款同步应答失败但回查确认已退款，按 SUCCESS 收口, orderNo={}, refundOrderNo={}, summaryAffected={}",
                refundDetail.getOrderNo(), refundDetail.getRefundOrderNo(), summaryAffected);
    }


    @Override
    public BaseRespDTO queryRefundResult(String refundOrderNo) {
        BaseRespDTO response = new BaseRespDTO();
        try {
            if (!StringUtils.hasText(refundOrderNo)) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "退款流水号不能为空");
                return response;
            }
            if (!refundGatewayPort.refundQueryConfigured()) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "未配置退款查询地址");
                return response;
            }
            Map<String, Object> bizData = new LinkedHashMap<>();
            bizData.put("refundOrderNo", refundOrderNo);
            bizData.put("merchantRefundNo", refundOrderNo);
            GatewayReply reply = refundGatewayPort.queryRefund(bizData);
            PaySignGatewayResponse gatewayResponse = reply.raw();
            log.info("只读退款回查, refundOrderNo={}, gatewayResponse={}",
                    refundOrderNo, JSON.toJSONString(gatewayResponse));
            fillSuccess(response);
            response.setCode(gatewayResponse == null ? null : gatewayResponse.getCode());
            response.setMsg(gatewayResponse == null ? null : gatewayResponse.getMsg());
            response.setSuccess(reply instanceof GatewayReply.Accepted);
            response.setData(gatewayResponse == null ? null : gatewayResponse.getData());
            return response;
        } catch (Exception e) {
            log.error("只读退款回查异常, refundOrderNo={}", refundOrderNo, e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }


    @Override
    public PaySignCallbackResult receiveRefundResult(ReceiveRefundResultReqDTO request) {
        PaySignCallbackResult response = new PaySignCallbackResult();
        try {
            if (request == null || !StringUtils.hasText(request.getOutRefundNo())) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "商户退款流水号（outRefundNo）不能为空");
                log.warn("§5.2 退款回调缺少 outRefundNo，无法定位本地退款单, request={}", JSON.toJSONString(request));
                return response;
            }

            String refundOrderNo = request.getOutRefundNo().trim();
            PayRefundDetail row = payRefundDetailMapper.selectByRefundOrderNo(refundOrderNo);
            if (row == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "未找到对应的退款单");
                log.warn("§5.2 退款回调找不到本地退款单，MUST 人工核对这笔退款归属, outRefundNo={}, request={}",
                        refundOrderNo, JSON.toJSONString(request));
                return response;
            }

            String settledStatus = convertPayStatus(request.getRefundResult());
            if (!"SUCCESS".equals(settledStatus) && !"FAIL".equals(settledStatus)) {
                fillSuccess(response);
                log.info("§5.2 退款回调非终态，不写库、交 sys_job 305 退款回查收口, refundOrderNo={}, txnDate={}, refundResult={}",
                        refundOrderNo, row.getTxnDate(), request.getRefundResult());
                return response;
            }

            PayRefundDetail update = new PayRefundDetail();
            update.setRefundOrderNo(row.getRefundOrderNo());
            update.setTxnDate(row.getTxnDate());
            update.setRefundStatus(settledStatus);
            update.setRefundNo(request.getRefundNo());
            update.setRefundTime(request.getRefundDate());
            update.setPayCenterCode(request.getRefundResult());
            update.setPayCenterMsg(request.getRefundResultDesc());
            update.setResponseBody(JSON.toJSONString(request));

            warnIfRefundAmountMismatch(row, request.getRefundAmount());

            int affected = payRefundDetailMapper.finishFromQuery(update);
            if (affected == 0) {
                fillSuccess(response);
                log.info("§5.2 退款回调收口 CAS 命中 0 行，已被退款回查收口，本次不重算汇总, refundOrderNo={}, txnDate={}, 回调状态={}",
                        refundOrderNo, row.getTxnDate(), settledStatus);
                return response;
            }

            int summaryAffected = payTxnDetailMapper.updateRefundSummary(row.getOrderNo());
            if (summaryAffected == 0) {
                log.error("§5.2 退款回调后汇总回写未命中原支付订单，MUST 人工核对 PAY_TXN_DETAIL 与 PAY_REFUND_DETAIL, "
                        + "orderNo={}, refundOrderNo={}", row.getOrderNo(), refundOrderNo);
            }
            fillSuccess(response);
            log.info("§5.2 退款回调已收口, refundOrderNo={}, txnDate={}, refundStatus={}, summaryAffected={}",
                    refundOrderNo, row.getTxnDate(), settledStatus, summaryAffected);
            return response;
        } catch (Exception e) {
            log.error("处理 §5.2 退款回调异常, request={}", JSON.toJSONString(request), e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

        private static final int REFUND_QUERY_SCAN_DAYS = 7;

    private static final int REFUND_QUERY_STALE_MINUTES = 5;

    private static final int REFUND_QUERY_RETRY_DELAY_SECONDS = 300;


    private static final int REFUND_QUERY_GIVE_UP_HOURS = 6;

    private static final int REFUND_QUERY_BATCH_SIZE = 200;

    @Override
    public CompensateNotifyRespDTO compensateRefundQuery() {
        CompensateNotifyRespDTO response = new CompensateNotifyRespDTO();
        try {
            if (!refundGatewayPort.refundQueryConfigured()) {
                log.error("未配置 pay.sign.refund-query-url，退款回查无法进行，停在 PROCESSING 的退款单本轮无人收口");
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "未配置退款查询地址");
                return response;
            }
            String txnDateFrom = LocalDateTime.now().minusDays(REFUND_QUERY_SCAN_DAYS)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            List<PayRefundDetail> pending = payRefundDetailMapper.selectCompensableRefundQuery(
                    txnDateFrom, REFUND_QUERY_STALE_MINUTES, REFUND_QUERY_BATCH_SIZE);
            if (pending == null || pending.isEmpty()) {
                fillSuccess(response);
                return response;
            }
            OutboxScan.Result scan = OutboxScan.run(pending,
                    this::settleRefundByQuery,
                    row -> log.warn("退款回查本轮未收口，等下次重扫, refundOrderNo={}, txnDate={}",
                            row.getRefundOrderNo(), row.getTxnDate()),
                    (row, e) -> log.error("单条退款回查异常，NEVER 因此中断整批, refundOrderNo={}, txnDate={}",
                            row.getRefundOrderNo(), row.getTxnDate(), e));
            response.setScanned(scan.scanned());
            response.setSubmitted(scan.success());
            response.setSkipped(scan.failed());
            log.info("退款回查补偿完成, txnDateFrom={}, scanned={}, settled={}, pendingAgain={}",
                    txnDateFrom, scan.scanned(), scan.success(), scan.failed());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("退款回查补偿异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }


    private boolean settleRefundByQuery(PayRefundDetail row) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("refundOrderNo", row.getRefundOrderNo());
        bizData.put("merchantRefundNo", row.getRefundOrderNo());
        GatewayReply queryReply = refundGatewayPort.queryRefund(bizData);
        log.info("退款回查支付中心返回, refundOrderNo={}, txnDate={}, gatewayResponse={}",
                row.getRefundOrderNo(), row.getTxnDate(), JSON.toJSONString(queryReply.raw()));

        String settledStatus = resolveRefundQueryStatus(queryReply);
        if (settledStatus == null) {
            if (exceededGiveUpAge(row)) {
                settleExhausted(row, queryReply);
                return false;
            }
            int delayed = payRefundDetailMapper.delayNextRefundQuery(
                    row.getRefundOrderNo(), row.getTxnDate(), REFUND_QUERY_RETRY_DELAY_SECONDS);
            if (delayed == 0) {
                log.info("退款回查未得终态且退避 CAS 命中 0 行，说明这一行已被别的路径收口, refundOrderNo={}, txnDate={}",
                        row.getRefundOrderNo(), row.getTxnDate());
            }
            return false;
        }

        PayRefundDetail update = new PayRefundDetail();
        update.setRefundOrderNo(row.getRefundOrderNo());
        update.setTxnDate(row.getTxnDate());
        update.setRefundStatus(settledStatus);
        PaySignGatewayResponse gatewayResponse = queryReply.raw();
        Map<String, Object> data = gatewayResponse.getData();
        update.setMerchantRefundNo(stringValue(data.get("merchantRefundNo"), null));
        update.setRefundNo(stringValue(data.get("refundNo"), null));
        update.setChannelRefundNo(stringValue(data.get("channelRefundNo"), null));
        update.setRefundTime(stringValue(data.get("refundTime"), null));
        update.setPayCenterCode(gatewayResponse.getCode() == null ? null : String.valueOf(gatewayResponse.getCode()));
        update.setPayCenterMsg(gatewayResponse.getMsg());
        update.setResponseBody(JSON.toJSONString(gatewayResponse));

        warnIfRefundAmountMismatch(row, data.get("refundAmount"));

        int affected = payRefundDetailMapper.finishFromQuery(update);
        if (affected == 0) {
            log.warn("退款回查收口 CAS 命中 0 行，已被其它路径收口，本轮不重算汇总, refundOrderNo={}, txnDate={}, 回查状态={}",
                    row.getRefundOrderNo(), row.getTxnDate(), settledStatus);
            return false;
        }

        int summaryAffected = payTxnDetailMapper.updateRefundSummary(row.getOrderNo());
        if (summaryAffected == 0) {
            log.error("退款回查后汇总回写未命中原支付订单，MUST 人工核对 PAY_TXN_DETAIL 与 PAY_REFUND_DETAIL, orderNo={}, refundOrderNo={}",
                    row.getOrderNo(), row.getRefundOrderNo());
        }
        log.info("退款回查已收口, refundOrderNo={}, txnDate={}, refundStatus={}, summaryAffected={}",
                row.getRefundOrderNo(), row.getTxnDate(), settledStatus, summaryAffected);
        return true;
    }


    private boolean exceededGiveUpAge(PayRefundDetail row) {
        LocalDateTime createTime = row.getCreateTime();
        if (createTime == null) {
            return false;
        }
        return createTime.isBefore(LocalDateTime.now().minusHours(REFUND_QUERY_GIVE_UP_HOURS));
    }


    private void settleExhausted(PayRefundDetail row, GatewayReply queryReply) {
        PaySignGatewayResponse gatewayResponse = queryReply == null ? null : queryReply.raw();
        String payCenterCode = gatewayResponse == null || gatewayResponse.getCode() == null
                ? null : String.valueOf(gatewayResponse.getCode());
        String payCenterMsg = gatewayResponse == null ? null : gatewayResponse.getMsg();
        int affected = payRefundDetailMapper.exhaustFromQuery(
                row.getRefundOrderNo(), row.getTxnDate(), payCenterCode, payCenterMsg);
        if (affected == 0) {
            log.info("退款回查已超龄但收口 CAS 命中 0 行，说明这一行已被别的路径收口, refundOrderNo={}, txnDate={}",
                    row.getRefundOrderNo(), row.getTxnDate());
            return;
        }
        log.error("退款回查超过 {} 小时仍拿不到终态，已落 CLOSED 停止轮询，MUST 人工到支付中心侧核账这笔到底退没退, "
                        + "refundOrderNo={}, orderNo={}, txnDate={}, refundAmount={}, createTime={}, "
                        + "支付中心最后应答 code={}, msg={}",
                REFUND_QUERY_GIVE_UP_HOURS, row.getRefundOrderNo(), row.getOrderNo(), row.getTxnDate(),
                row.getRefundAmount(), row.getCreateTime(), payCenterCode, payCenterMsg);
    }


    private void warnIfRefundAmountMismatch(PayRefundDetail row, Object queriedAmount) {
        if (queriedAmount == null) {
            return;
        }
        Integer localAmount = row.getRefundAmount();
        if (localAmount == null) {
            return;
        }
        try {
            long queried = queriedAmount instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(queriedAmount).trim());
            if (localAmount.longValue() != queried) {
                log.warn("退款回查：金额与本地退款单不一致，只告警不阻断收口, refundOrderNo={}, txnDate={}, localAmount={}, queriedAmount={}",
                        row.getRefundOrderNo(), row.getTxnDate(), localAmount, queried);
            }
        } catch (NumberFormatException e) {
            log.warn("退款回查：refundAmount 不是整数分，跳过金额比对, refundOrderNo={}, txnDate={}, refundAmount={}",
                    row.getRefundOrderNo(), row.getTxnDate(), queriedAmount);
        }
    }

    private String resolveRefundQueryStatus(GatewayReply reply) {
        if (!(reply instanceof GatewayReply.Accepted accepted) || accepted.data() == null) {
            return null;
        }
        String status = stringValue(accepted.data().get("status"), null);
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = convertPayStatus(status);
        if ("SUCCESS".equals(normalized) || "FAIL".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private static final int REFUND_SUMMARY_SCAN_DAYS = 7;

    private static final int REFUND_SUMMARY_BATCH_SIZE = 200;

    @Override
    public CompensateNotifyRespDTO compensateRefundSummary() {
        CompensateNotifyRespDTO response = new CompensateNotifyRespDTO();
        try {
            String txnDateFrom = LocalDateTime.now().minusDays(REFUND_SUMMARY_SCAN_DAYS)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            List<String> drifted = payRefundDetailMapper.selectDriftedRefundSummary(
                    txnDateFrom, REFUND_SUMMARY_BATCH_SIZE);
            List<String> orphans = payRefundDetailMapper.selectOrphanRefundOrders(
                    txnDateFrom, REFUND_SUMMARY_BATCH_SIZE);

            OutboxScan.Result driftScan = OutboxScan.run(drifted,
                    orderNo -> payTxnDetailMapper.updateRefundSummary(orderNo) > 0,
                    orderNo -> log.warn("退款汇总重算影响 0 行，该单在重算前已被别的路径改掉或已消失，"
                            + "本轮不计入已修，留给下一轮, orderNo={}", orderNo),
                    (orderNo, e) -> log.error("单条退款汇总重算异常，NEVER 因此中断整批, orderNo={}", orderNo, e));

            OutboxScan.Result orphanScan = OutboxScan.run(orphans,
                    orderNo -> false,
                    orderNo -> log.warn("退款明细已 SUCCESS 但原支付订单不存在，MUST 人工核对这笔退款对应哪张原单，"
                            + "本任务不会自愈, orderNo={}", orderNo),
                    (orderNo, e) -> log.error("单条孤儿退款订单登记异常，NEVER 因此中断整批, orderNo={}", orderNo, e));

            response.setScanned(driftScan.scanned() + orphanScan.scanned());
            response.setSubmitted(driftScan.success() + orphanScan.success());
            response.setSkipped(driftScan.failed() + orphanScan.failed());
            log.info("退款汇总跨表对账补偿完成, txnDateFrom={}, scanned={}, submitted={}, skipped={}, "
                            + "其中不可自愈（原单不存在）={}",
                    txnDateFrom, response.getScanned(), response.getSubmitted(), response.getSkipped(),
                    orphanScan.scanned());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("退款汇总跨表对账补偿异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    private void updateRefundRequestResult(PayRefundDetail refundDetail, String refundStatus,
                                           RequestRefundResult response, PaySignGatewayResponse gatewayResponse) {
        PayRefundDetail update = new PayRefundDetail();
        update.setRefundOrderNo(refundDetail.getRefundOrderNo());
        update.setTxnDate(refundDetail.getTxnDate());
        update.setRefundStatus(refundStatus);
        update.setMerchantRefundNo(response.getMerchantRefundNo());
        update.setRefundNo(response.getRefundNo());
        update.setChannelRefundNo(response.getChannelRefundNo());
        update.setRefundTime(response.getRefundTime());
        update.setRetCode(response.getRetCode());
        update.setRetMsg(response.getRetMsg());
        update.setPayCenterCode(gatewayResponse == null ? null : String.valueOf(gatewayResponse.getCode()));
        update.setPayCenterMsg(gatewayResponse == null ? null : gatewayResponse.getMsg());
        update.setResponseBody(JSON.toJSONString(gatewayResponse));
        payRefundDetailMapper.updateRequestResult(update);
    }
    private void fillGatewayFields(RequestRefundResult response, GatewayReply reply) {
        PaySignGatewayResponse gatewayResponse = reply == null ? null : reply.raw();
        if (gatewayResponse == null) {
            return;
        }
        response.setCode(gatewayResponse.getCode());
        response.setMsg(gatewayResponse.getMsg());
        response.setSuccess(reply instanceof GatewayReply.Accepted);
        response.setData(gatewayResponse.getData());
    }
}
