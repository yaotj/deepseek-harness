package com.chinasofti.huateng.alipay.paysign.service.impl.payment;

import com.chinasofti.huateng.alipay.paysign.service.impl.support.AlipayPayCenterMsgLogWriter;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.BizDataBuilder;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.IndustryDetailEnricher;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.port.BlacklistPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;


@Service
public class AlipayTxnPayService {

    private static final Logger log = LoggerFactory.getLogger(AlipayTxnPayService.class);

    private static final String CHANNEL_ALIPAY = "ALIPAY";
    private static final String API_REQUEST_PAY = "requestPay";

    private static final String PAY_STATUS_INIT = "INIT";
    private static final String PAY_STATUS_PROCESSING = "PROCESSING";
    private static final String PAY_STATUS_SUCCESS = "SUCCESS";
    private static final String PAY_STATUS_FAIL = "FAIL";
    private static final String REFUND_STATUS_NONE = "NONE";

    private static final String PAY_CENTER_SUCCESS = "SUCCESS";

    private final AlipaySignInfoMapper alipaySignInfoMapper;
    private final AlipayPayTxnDetailMapper alipayPayTxnDetailMapper;
    private final AlipayPayCenterMsgLogWriter msgLogWriter;
    private final GateTxnPayClient gateTxnPayClient;
    private final IndustryDetailEnricher industryDetailEnricher;
    private final BizDataBuilder bizDataBuilder;
    private final PayCenterProperties payCenterProperties;
    private final PayCenterPort payCenterPort;
    private final BlacklistPort blacklistPort;

    public AlipayTxnPayService(AlipaySignInfoMapper alipaySignInfoMapper,
                              AlipayPayTxnDetailMapper alipayPayTxnDetailMapper,
                              AlipayPayCenterMsgLogWriter msgLogWriter,
                              GateTxnPayClient gateTxnPayClient,
                              IndustryDetailEnricher industryDetailEnricher,
                              BizDataBuilder bizDataBuilder,
                              PayCenterProperties payCenterProperties,
                              PayCenterPort payCenterPort,
                              BlacklistPort blacklistPort) {
        this.alipaySignInfoMapper = alipaySignInfoMapper;
        this.alipayPayTxnDetailMapper = alipayPayTxnDetailMapper;
        this.msgLogWriter = msgLogWriter;
        this.gateTxnPayClient = gateTxnPayClient;
        this.industryDetailEnricher = industryDetailEnricher;
        this.bizDataBuilder = bizDataBuilder;
        this.payCenterProperties = payCenterProperties;
        this.payCenterPort = payCenterPort;
        this.blacklistPort = blacklistPort;
    }

    public AlipayTripRequestPayRespDTO requestPay(AlipayTripRequestPayReqDTO request) {
        log.info("接收到支付宝支付申请报文（新链路）: {}", JSON.toJSONString(request));
        validate(request);

        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByThirdUserIdAndChannel(request.getThirdUserId(), CHANNEL_ALIPAY);
        if (signInfo == null) {
            throw new BusinessException(FepAppErrorCodeEnum.USER_NOT_SIGNED.getCode(), "用户未签约");
        }
        request.setRequestSignSeq(signInfo.getAgreementCode());

        String orderNo = request.getOrderNo();
        String txnDate = resolveTxnDate(orderNo);

        AlipayPayTxnDetail existing = alipayPayTxnDetailMapper.selectByOrderNo(orderNo);
        if (existing != null && PAY_STATUS_SUCCESS.equals(existing.getPayStatus())) {
            log.info("支付明细已是终态 SUCCESS，按重推短路返成功, orderNo={}", orderNo);
            return response(FepAppErrorCodeEnum.SUCCESS.getCode(), "支付成功", orderNo);
        }
        if (existing == null) {
            insertDetail(request, signInfo, orderNo, txnDate);
        }

        alipayPayTxnDetailMapper.markRequesting(orderNo);

        request.setIndustryDetail(industryDetailEnricher.enrich(request.getIndustryDetail(), signInfo.getThirdUserId()));
        Map<String, Object> bizData = bizDataBuilder.build(request, signInfo, payCenterProperties);
        log.info("支付宝支付申请,调用支付中心,orderNo={}, 请求参数={}", orderNo, JSON.toJSONString(bizData));

        PayCenterReply reply;
        long startNanos = System.nanoTime();
        try {
            reply = payCenterPort.requestPay(bizData);
        } catch (RuntimeException e) {
            long elapsed = elapsedMillis(startNanos);
            msgLogWriter.record(orderNo, txnDate, API_REQUEST_PAY, null, bizData, null, elapsed,
                    "调用支付中心抛异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("支付宝支付申请调用支付中心抛异常，状态保持 PROCESSING 等回查、NEVER 置 FAIL, orderNo={}", orderNo, e);
            return response(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "调用支付中心失败", orderNo);
        }
        long elapsedMs = elapsedMillis(startNanos);
        msgLogWriter.record(orderNo, txnDate, API_REQUEST_PAY, null, bizData, reply, elapsedMs, null);

        return applyReply(reply, signInfo, orderNo);
    }


    private AlipayTripRequestPayRespDTO applyReply(PayCenterReply reply, AlipaySignInfo signInfo, String orderNo) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                log.info("支付宝支付申请,支付中心业务应答: orderNo={}, retCode={}, retMsg={}",
                        orderNo, accepted.retCode(), accepted.retMsg());
                if (PAY_CENTER_SUCCESS.equals(accepted.retCode())) {
                    updateRequestResult(orderNo, PAY_STATUS_PROCESSING,
                            accepted.field("payCenterOrderNo"), accepted.field("channelOrderNo"));
                    String msg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "支付受理成功";
                    return response(FepAppErrorCodeEnum.SUCCESS.getCode(), msg, orderNo);
                }
                updateRequestResult(orderNo, PAY_STATUS_FAIL, null, accepted.field("channelOrderNo"));
                String msg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "支付失败";
                addBlackListIfNeeded(signInfo, msg);
                return response(FepAppErrorCodeEnum.FAIL.getCode(), msg, orderNo);
            }
            case PayCenterReply.Rejected rejected -> {
                log.error("支付宝支付申请未拿到业务应答，置 FAIL、NEVER 加黑名单, orderNo={}, code={}, msg={}, success={}",
                        orderNo, rejected.code(), rejected.msg(), rejected.success());
                updateRequestResult(orderNo, PAY_STATUS_FAIL, null, null);
                return response(FepAppErrorCodeEnum.FAIL.getCode(),
                        rejected.msg() != null ? rejected.msg() : "支付失败", orderNo);
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                log.error("支付宝支付申请无响应，状态保持 PROCESSING 等回查、NEVER 置 FAIL、NEVER 加黑名单, orderNo={}", orderNo);
                return response(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "调用支付中心失败", orderNo);
            }
        }
    }


    private String resolveTxnDate(String orderNo) {
        GateTxnPayListDTO order = gateTxnPayClient.queryByOrderNo(orderNo);
        if (order == null || !StringUtils.hasText(order.getTxnDate())) {
            log.error("查订单主表拿不到 txnDate，拒绝落支付明细、让上游重推, orderNo={}, order={}", orderNo, order);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "订单不存在或缺少交易日期");
        }
        return order.getTxnDate();
    }


    private void insertDetail(AlipayTripRequestPayReqDTO request, AlipaySignInfo signInfo, String orderNo, String txnDate) {
        AlipayPayTxnDetail record = new AlipayPayTxnDetail();
        record.setOrderNo(orderNo);
        record.setTxnDate(txnDate);
        record.setPayStatus(PAY_STATUS_INIT);
        record.setAmount(request.getAmount());
        record.setRefundStatus(REFUND_STATUS_NONE);
        record.setRefundAmount(0);
        record.setRequestSignSeq(signInfo.getAgreementCode());
        record.setChannelAgreementNo(signInfo.getChannelAgreementCode());
        record.setRequestCount(0);
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            alipayPayTxnDetailMapper.insert(record);
        } catch (RuntimeException e) {
            if (!isIntegrityViolation(e)) {
                throw e;
            }
            log.info("支付明细已被并发请求落好，按幂等继续, orderNo={}", orderNo);
        }
    }

    private void updateRequestResult(String orderNo, String payStatus, String payCenterOrderNo, String channelOrderNo) {
        AlipayPayTxnDetail update = new AlipayPayTxnDetail();
        update.setOrderNo(orderNo);
        update.setPayStatus(payStatus);
        update.setPayCenterOrderNo(payCenterOrderNo);
        update.setChannelOrderNo(channelOrderNo);
        int affected = alipayPayTxnDetailMapper.updateRequestResult(update);
        if (affected == 0) {
            log.error("回写支付申请结果影响 0 行，明细行可能不存在、MUST 人工核对, orderNo={}, payStatus={}", orderNo, payStatus);
        }
    }


    private void addBlackListIfNeeded(AlipaySignInfo signInfo, String reason) {
        if (signInfo == null || !StringUtils.hasText(signInfo.getCardId()) || !StringUtils.hasText(signInfo.getThirdUserId())) {
            return;
        }
        String cardId = signInfo.getCardId();
        String thirdUserId = signInfo.getThirdUserId();
        String blackReason = StringUtils.hasText(reason) ? reason : "地铁扣款失败";
        RpcOutcome outcome = blacklistPort.addBlackList(cardId, thirdUserId, signInfo.getCardType(), blackReason);
        switch (outcome) {
            case RpcOutcome.Ok ok -> log.info("扣款失败加黑名单完成, cardId={}", cardId);
            case RpcOutcome.BizRejected rejected -> log.error(
                    "加黑名单被业务拒绝，重试无意义、该卡仍可过闸、MUST 人工核对, cardId={}, retCode={}, retMsg={}",
                    cardId, rejected.retCode(), rejected.retMsg());
            case RpcOutcome.Unreachable unreachable -> log.error(
                    "加黑名单未获答复，该卡仍可过闸、MUST 人工核对, cardId={}, cause={}",
                    cardId, unreachable.cause().getClass().getSimpleName(), unreachable.cause());
        }
    }


    private void validate(AlipayTripRequestPayReqDTO request) {
        boolean valid = request != null
                && StringUtils.hasText(request.getOrderNo())
                && StringUtils.hasText(request.getThirdUserId())
                && request.getAmount() != null
                && StringUtils.hasText(request.getIndustryType())
                && StringUtils.hasText(request.getSubject())
                && StringUtils.hasText(request.getBody())
                && StringUtils.hasText(request.getIndustryDetail());
        if (!valid) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(),
                    "订单号/第三方用户号/支付金额/行业类型/订单标题/订单描述/行业详情不能为空");
        }
    }

    private AlipayTripRequestPayRespDTO response(String retCode, String retMsg, String orderNo) {
        AlipayTripRequestPayRespDTO response = new AlipayTripRequestPayRespDTO();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        response.setOrderNo(orderNo);
        log.info("支付宝支付申请完成（新链路）, orderNo={}, retCode={}", orderNo, retCode);
        return response;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private boolean isIntegrityViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof org.springframework.dao.DataIntegrityViolationException) {
                return true;
            }
            String name = current.getClass().getName();
            if (name.contains("DuplicateKey") || name.contains("IntegrityConstraintViolation")) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }
}
