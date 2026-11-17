package com.chinasofti.huateng.alipay.paysign.service.impl.pay;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.domain.PayCenterTradeStatus;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.port.BlacklistPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPayRequestService;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.AlipayPayCenterMsgLogWriter;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.BizDataBuilder;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.IndustryDetailEnricher;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;


@Service
public class AlipayPayRequestServiceImpl implements AlipayPayRequestService {

    private static final Logger log = LoggerFactory.getLogger(AlipayPayRequestServiceImpl.class);

    private static final String CHANNEL_ALIPAY = "ALIPAY";
    private static final String API_REQUEST_PAY = "requestPay";
    private static final String PAY_CENTER_SUCCESS = "SUCCESS";
    private static final String CARD_ISSUE_CODE_ALIPAY = "0007";

    private final AlipaySignInfoMapper alipaySignInfoMapper;
    private final PayTxnRepository payTxnRepository;
    private final AlipayPayCenterMsgLogWriter msgLogWriter;
    private final GateTxnPayClient gateTxnPayClient;
    private final IndustryDetailEnricher industryDetailEnricher;
    private final BizDataBuilder bizDataBuilder;
    private final PayCenterProperties payCenterProperties;
    private final PayCenterPort payCenterPort;
    private final BlacklistPort blacklistPort;

    public AlipayPayRequestServiceImpl(AlipaySignInfoMapper alipaySignInfoMapper,
                                       PayTxnRepository payTxnRepository,
                                       AlipayPayCenterMsgLogWriter msgLogWriter,
                                       GateTxnPayClient gateTxnPayClient,
                                       IndustryDetailEnricher industryDetailEnricher,
                                       BizDataBuilder bizDataBuilder,
                                       PayCenterProperties payCenterProperties,
                                       PayCenterPort payCenterPort,
                                       BlacklistPort blacklistPort) {
        this.alipaySignInfoMapper = alipaySignInfoMapper;
        this.payTxnRepository = payTxnRepository;
        this.msgLogWriter = msgLogWriter;
        this.gateTxnPayClient = gateTxnPayClient;
        this.industryDetailEnricher = industryDetailEnricher;
        this.bizDataBuilder = bizDataBuilder;
        this.payCenterProperties = payCenterProperties;
        this.payCenterPort = payCenterPort;
        this.blacklistPort = blacklistPort;
    }

    @Override
    public AlipayTripRequestPayRespDTO requestPay(AlipayTripRequestPayReqDTO request) {
        log.info("接收到支付宝支付申请报文: {}", JSON.toJSONString(request));
        PayCommand command = PayCommand.from(request);
        String orderNo = command.orderNo();

        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByThirdUserIdAndChannel(command.thirdUserId(), CHANNEL_ALIPAY);
        if (signInfo == null) {
            throw new BusinessException(FepAppErrorCodeEnum.USER_NOT_SIGNED.getCode(), "用户未签约");
        }

        AlipayPayTxnDetail existing = payTxnRepository.findByOrderNo(orderNo);
        if (PayTxnRepository.isSettled(existing)) {
            log.info("支付明细已是终态 SUCCESS，按重推短路返成功, orderNo={}", orderNo);
            return response(FepAppErrorCodeEnum.SUCCESS.getCode(), "支付成功", orderNo);
        }

        String txnDate = resolveTxnDate(orderNo);
        payTxnRepository.openAttempt(command, signInfo, txnDate, existing);

        return callPayCenter(request, command, signInfo, txnDate);
    }


    private AlipayTripRequestPayRespDTO callPayCenter(AlipayTripRequestPayReqDTO request,
                                                      PayCommand command,
                                                      AlipaySignInfo signInfo,
                                                      String txnDate) {
        String orderNo = command.orderNo();
        request.setRequestSignSeq(signInfo.getAgreementCode());
        request.setIndustryDetail(industryDetailEnricher.enrich(command.industryDetail(), signInfo.getThirdUserId()));
        Map<String, Object> bizData = bizDataBuilder.build(request, signInfo, payCenterProperties);
        log.info("支付宝支付申请,调用支付中心,orderNo={}, 请求参数={}", orderNo, JSON.toJSONString(bizData));

        PayCenterReply reply;
        long startNanos = System.nanoTime();
        try {
            reply = payCenterPort.requestPay(bizData);
        } catch (RuntimeException e) {
            msgLogWriter.record(orderNo, txnDate, API_REQUEST_PAY, null, bizData, null, elapsedMillis(startNanos),
                    "调用支付中心抛异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("支付宝支付申请调用支付中心抛异常，状态保持 PROCESSING 等回查、NEVER 置 FAIL, orderNo={}", orderNo, e);
            return response(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "调用支付中心失败", orderNo);
        }
        msgLogWriter.record(orderNo, txnDate, API_REQUEST_PAY, null, bizData, reply, elapsedMillis(startNanos), null);

        return applyReply(reply, signInfo, orderNo);
    }


    private AlipayTripRequestPayRespDTO applyReply(PayCenterReply reply, AlipaySignInfo signInfo, String orderNo) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                log.info("支付宝支付申请,支付中心业务应答: orderNo={}, retCode={}, retMsg={}",
                        orderNo, accepted.retCode(), accepted.retMsg());
                if (PAY_CENTER_SUCCESS.equals(accepted.retCode())) {
                    payTxnRepository.writeRequestResult(orderNo, PayTxnRepository.STATUS_PROCESSING,
                            accepted.field("payCenterOrderNo"), accepted.field("channelOrderNo"));
                    String msg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "支付受理成功";
                    return response(FepAppErrorCodeEnum.SUCCESS.getCode(), msg, orderNo);
                }
                payTxnRepository.writeRequestResult(orderNo, PayTxnRepository.STATUS_FAIL,
                        null, accepted.field("channelOrderNo"));
                String msg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "支付失败";
                if (confirmedFailedByPayQuery(orderNo, signInfo)) {
                    addBlackListIfNeeded(signInfo, msg);
                } else {
                    log.warn("扣费被拒但回查确认不了这笔真的失败，跳过加黑名单、MUST 人工核对, orderNo={}, retCode={}, retMsg={}",
                            orderNo, accepted.retCode(), accepted.retMsg());
                }
                return response(FepAppErrorCodeEnum.FAIL.getCode(), msg, orderNo);
            }
            case PayCenterReply.Rejected rejected -> {
                log.error("支付宝支付申请未拿到业务应答，置 FAIL、NEVER 加黑名单, orderNo={}, code={}, msg={}, success={}",
                        orderNo, rejected.code(), rejected.msg(), rejected.success());
                payTxnRepository.writeRequestResult(orderNo, PayTxnRepository.STATUS_FAIL, null, null);
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


    private boolean confirmedFailedByPayQuery(String orderNo, AlipaySignInfo signInfo) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", orderNo);
        bizData.put("cardIssueCode", CARD_ISSUE_CODE_ALIPAY);
        if (signInfo != null && StringUtils.hasText(signInfo.getChannelAgreementCode())) {
            bizData.put("channelAgreementNo", signInfo.getChannelAgreementCode());
        }

        PayCenterReply reply;
        try {
            reply = payCenterPort.payQuery(bizData);
        } catch (RuntimeException e) {
            log.error("加黑前回查支付中心抛异常，按「确认不了」处置、不加黑, orderNo={}", orderNo, e);
            return false;
        }

        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                String tradeStatus = firstNonBlank(accepted.field("status"), accepted.field("tradeStatus"));
                log.info("加黑前回查支付中心应答: orderNo={}, retCode={}, retMsg={}, tradeStatus={}",
                        orderNo, accepted.retCode(), accepted.retMsg(), tradeStatus);
                if (!PAY_CENTER_SUCCESS.equals(accepted.retCode())) {
                    log.error("加黑前回查被支付中心拒绝，按「确认不了」处置、不加黑, orderNo={}, retCode={}, retMsg={}",
                            orderNo, accepted.retCode(), accepted.retMsg());
                    return false;
                }
                if (PayCenterTradeStatus.isConfirmedFail(tradeStatus)) {
                    return true;
                }
                log.error("加黑前回查拿到的交易状态不是确定失败，不加黑、MUST 人工到支付中心核对, orderNo={}, tradeStatus={}",
                        orderNo, tradeStatus);
                return false;
            }
            case PayCenterReply.Rejected rejected -> {
                log.error("加黑前回查未拿到业务应答，按「确认不了」处置、不加黑, orderNo={}, code={}, msg={}, success={}",
                        orderNo, rejected.code(), rejected.msg(), rejected.success());
                return false;
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                log.error("加黑前回查无响应，按「确认不了」处置、不加黑, orderNo={}", orderNo);
                return false;
            }
        }
    }


    private void addBlackListIfNeeded(AlipaySignInfo signInfo, String reason) {
        if (signInfo == null || !StringUtils.hasText(signInfo.getCardId()) || !StringUtils.hasText(signInfo.getThirdUserId())) {
            return;
        }
        String cardId = signInfo.getCardId();
        String blackReason = StringUtils.hasText(reason) ? reason : "地铁扣款失败";
        RpcOutcome outcome = blacklistPort.addBlackList(cardId, signInfo.getThirdUserId(), signInfo.getCardType(), blackReason);
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

    private AlipayTripRequestPayRespDTO response(String retCode, String retMsg, String orderNo) {
        AlipayTripRequestPayRespDTO response = new AlipayTripRequestPayRespDTO();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        response.setOrderNo(orderNo);
        log.info("支付宝支付申请完成, orderNo={}, retCode={}", orderNo, retCode);
        return response;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
