package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPayRefundService;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.RefundAmountCalculator;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;


@Service
public class AlipayPayRefundServiceImpl implements AlipayPayRefundService {

    private static final Logger log = LoggerFactory.getLogger(AlipayPayRefundServiceImpl.class);

    private static final String CHANNEL_ALIPAY = "ALIPAY";
    private static final String CARD_ISSUE_CODE_ALIPAY = "0007";
    private static final String PAY_STATUS_SUCCESS = "SUCCESS";
    private static final String PAY_CENTER_SUCCESS = "SUCCESS";

    private final AlipaySignInfoMapper alipaySignInfoMapper;
    private final RefundAmountCalculator refundAmountCalculator;
    private final RefundLogRepository refundLogRepository;
    private final PayCenterPort payCenterPort;
    private final PayCenterProperties payCenterProperties;

    public AlipayPayRefundServiceImpl(AlipaySignInfoMapper alipaySignInfoMapper,
                                     RefundAmountCalculator refundAmountCalculator,
                                     RefundLogRepository refundLogRepository,
                                     PayCenterPort payCenterPort,
                                     PayCenterProperties payCenterProperties) {
        this.alipaySignInfoMapper = alipaySignInfoMapper;
        this.refundAmountCalculator = refundAmountCalculator;
        this.refundLogRepository = refundLogRepository;
        this.payCenterPort = payCenterPort;
        this.payCenterProperties = payCenterProperties;
    }

    @Override
    public AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request) {
        log.info("接收到支付宝退款申请报文: {}", JSON.toJSONString(request));
        RefundCommand command = RefundCommand.from(request);
        String orderNo = command.orderNo();

        AlipayPayLog payLog = loadSettledPayLog(orderNo);
        String cardNum = payLog.getCardId();
        rejectIfProcessingRefundExists(orderNo);

        String channelAgreementNo = resolveChannelAgreementNo(cardNum);
        String refundAmount = refundAmountCalculator.resolveRefundAmount(command.refundAmount(), payLog);
        refundAmountCalculator.validateRefundAmount(refundAmount, payLog);

        String refundOrderNo = "R" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        String refundSeq = refundLogRepository.openRefund(command, payLog, channelAgreementNo,
                refundAmount, refundOrderNo, JSON.toJSONString(request));

        return callPayCenter(command, cardNum, channelAgreementNo, refundAmount, refundOrderNo, refundSeq);
    }


    private AlipayPayLog loadSettledPayLog(String orderNo) {
        AlipayPayLog payLog = refundLogRepository.findPayLog(orderNo);
        if (payLog == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录不存在");
        }
        if (!PAY_STATUS_SUCCESS.equals(payLog.getPayStatus())) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付订单未支付成功");
        }
        if (!StringUtils.hasText(payLog.getCardId())) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录缺少逻辑卡号");
        }
        return payLog;
    }

    private void rejectIfProcessingRefundExists(String orderNo) {
        int processingCount = refundLogRepository.countProcessing(orderNo);
        if (processingCount > 0) {
            log.error("该订单存在未收口的退款明细，拒绝重复退款，MUST 人工到支付中心核对上一笔结果, orderNo={}, processingCount={}",
                    orderNo, processingCount);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "该订单存在处理中的退款，请先确认上一笔结果");
        }
    }


    private String resolveChannelAgreementNo(String cardNum) {
        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByCardIdAndChannel(cardNum, CHANNEL_ALIPAY);
        String channelAgreementNo = signInfo != null ? signInfo.getChannelAgreementCode() : null;
        if (!StringUtils.hasText(channelAgreementNo)) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "退款申请未查到签约信息, cardNum=" + cardNum);
        }
        return channelAgreementNo;
    }


    private AlipayTripRequestRefundRespDTO callPayCenter(RefundCommand command, String cardNum,
                                                        String channelAgreementNo, String refundAmount,
                                                        String refundOrderNo, String refundSeq) {
        String orderNo = command.orderNo();
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", orderNo);
        bizData.put("cardIssueCode", CARD_ISSUE_CODE_ALIPAY);
        bizData.put("cardNum", cardNum);
        bizData.put("channelAgreementNo", channelAgreementNo);
        bizData.put("refundAmount", refundAmount);
        bizData.put("refundOrderNo", refundOrderNo);
        String refundNotifyUrl = payCenterProperties.getRefundNotifyUrl();
        if (StringUtils.hasText(refundNotifyUrl)) {
            bizData.put("notifyUrl", refundNotifyUrl);
        } else {
            log.warn("pay.center.refund-notify-url 未配置，本次退款申请不送 notifyUrl，支付中心将无法回推退款结果，MUST 配置后重试, orderNo={}, refundOrderNo={}",
                    orderNo, refundOrderNo);
        }

        log.info("支付宝出行-退款申请,调用支付中心退款接口,请求参数: {}", JSON.toJSONString(bizData));
        PayCenterReply reply = payCenterPort.requestRefund(bizData);
        log.info("支付宝退款申请,支付中心响应结果: code={}, success={}, msg={}", reply.code(), reply.success(), reply.msg());

        AlipayTripRequestRefundRespDTO response = applyReply(reply, orderNo, refundOrderNo, refundSeq);
        log.info("支付宝退款申请完成, orderNo={}, refundOrderNo={}, cardNum={}, channelAgreementNo={}, refundAmount={}, retCode={}",
                orderNo, refundOrderNo, cardNum, channelAgreementNo, refundAmount, response.getRetCode());
        return response;
    }


    private AlipayTripRequestRefundRespDTO applyReply(PayCenterReply reply, String orderNo,
                                                     String refundOrderNo, String refundSeq) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                log.info("支付宝退款申请,支付中心解密后数据: retCode={}, retMsg={}", accepted.retCode(), accepted.retMsg());
                boolean refundSuccess = PAY_CENTER_SUCCESS.equals(accepted.retCode());
                String resultCode = refundSuccess
                        ? FepAppErrorCodeEnum.SUCCESS.getCode() : FepAppErrorCodeEnum.FAIL.getCode();
                String resultMsg = resolveResultMsg(accepted, refundSuccess);
                refundLogRepository.writeResult(refundSeq,
                        refundSuccess ? RefundLogRepository.REFUND_STATUS_SUCCESS : RefundLogRepository.REFUND_STATUS_FAIL,
                        resultCode, resultMsg, accepted.rawBody());
                if (refundSuccess) {
                    refundLogRepository.refreshSummary(orderNo, refundOrderNo);
                }
                return response(resultCode, resultMsg);
            }
            case PayCenterReply.Rejected rejected -> {
                return keepProcessing(orderNo, refundOrderNo, refundSeq, rejected.code(), rejected.msg(),
                        rejected.rawBody());
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                return keepProcessing(orderNo, refundOrderNo, refundSeq, noAnswer.code(), noAnswer.msg(),
                        noAnswer.rawBody());
            }
        }
    }

    private AlipayTripRequestRefundRespDTO keepProcessing(String orderNo, String refundOrderNo, String refundSeq,
                                                         Integer code, String msg, String rawBody) {
        log.error("支付宝退款申请未拿到支付中心业务应答，退款结果未知、明细保持 PROCESSING，MUST 人工核对, orderNo={}, refundOrderNo={}, code={}, msg={}",
                orderNo, refundOrderNo, code, msg);
        refundLogRepository.writeResult(refundSeq, RefundLogRepository.REFUND_STATUS_PROCESSING,
                RefundLogRepository.RESULT_CODE_INIT, "退款结果未知，待人工核对", rawBody);
        return response(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "退款结果未知，请稍后核对");
    }

    private String resolveResultMsg(PayCenterReply.Accepted accepted, boolean refundSuccess) {
        if (StringUtils.hasText(accepted.retMsg())) {
            return accepted.retMsg();
        }
        if (refundSuccess) {
            return "退款成功";
        }
        return StringUtils.hasText(accepted.msg()) ? accepted.msg() : "退款失败";
    }

    private AlipayTripRequestRefundRespDTO response(String retCode, String retMsg) {
        AlipayTripRequestRefundRespDTO response = new AlipayTripRequestRefundRespDTO();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        return response;
    }
}
