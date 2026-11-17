package com.chinasofti.huateng.alipay.paysign.service.impl.payment;

import com.chinasofti.huateng.alipay.paysign.service.impl.support.RefundAmountCalculator;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCallbackLog;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundLog;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayCallbackLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentRefundService {
    private static final Logger log = LoggerFactory.getLogger(PaymentRefundService.class);
    private static final String CHANNEL_ALIPAY = "ALIPAY";

    private static final String REFUND_STATUS_PROCESSING = "PROCESSING";
    private static final String REFUND_STATUS_SUCCESS = "SUCCESS";
    private static final String REFUND_STATUS_FAIL = "FAIL";
    private static final String RESULT_CODE_INIT = "INIT";

    private static final String CALLBACK_TYPE_REFUND = "REFUND";
    private static final String HANDLE_STATUS_PENDING = "PENDING";

    @Autowired
    private AlipayPayLogMapper alipayPayLogMapper;

    @Autowired
    private AlipayPayCallbackLogMapper alipayPayCallbackLogMapper;

    @Autowired
    private AlipayRefundLogMapper alipayRefundLogMapper;

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    @Autowired
    private RefundAmountCalculator refundAmountCalculator;

    @Autowired
    private PayCenterPort payCenterPort;


    public AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request) {
        AlipayTripRequestRefundRespDTO response = new AlipayTripRequestRefundRespDTO();
        log.info("接收到支付宝退款申请报文: {}", JSON.toJSONString(request));

        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号不能为空");
        }

        AlipayPayLog payLog = alipayPayLogMapper.selectByOrderNo(request.getOrderNo());
        if (payLog == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录不存在");
        }

        if (!"SUCCESS".equals(payLog.getPayStatus())) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付订单未支付成功");
        }

        String cardNum = payLog.getCardId();
        if (!StringUtils.hasText(cardNum)) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录缺少逻辑卡号");
        }

        int processingCount = alipayRefundLogMapper.countByOrderNoAndStatus(request.getOrderNo(), REFUND_STATUS_PROCESSING);
        if (processingCount > 0) {
            log.error("该订单存在未收口的退款明细，拒绝重复退款，MUST 人工到支付中心核对上一笔结果, orderNo={}, processingCount={}",
                    request.getOrderNo(), processingCount);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "该订单存在处理中的退款，请先确认上一笔结果");
        }

        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByCardIdAndChannel(cardNum, CHANNEL_ALIPAY);
        String channelAgreementNo = signInfo != null ? signInfo.getChannelAgreementCode() : null;
        if (!StringUtils.hasText(channelAgreementNo)) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "退款申请未查到签约信息, cardNum=" + cardNum);
        }

        String refundAmount = refundAmountCalculator.resolveRefundAmount(request.getRefundAmount(), payLog);
        refundAmountCalculator.validateRefundAmount(refundAmount, payLog);

        String refundOrderNo = "R" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);

        AlipayRefundLog refundLog = new AlipayRefundLog();
        refundLog.setRefundSeq(UUID.randomUUID().toString().replaceAll("-", ""));
        refundLog.setThirdUserId(payLog.getThirdUserId());
        refundLog.setCardId(payLog.getCardId());
        refundLog.setOrderNo(request.getOrderNo());
        refundLog.setRefundAmount(refundAmount);
        refundLog.setRefundStatus(REFUND_STATUS_PROCESSING);
        refundLog.setCardIssueCode("0007");
        refundLog.setChannelAgreementNo(channelAgreementNo);
        refundLog.setRefundOrderNo(refundOrderNo);
        refundLog.setRequestBody(JSON.toJSONString(request));
        refundLog.setResponseBody("");
        refundLog.setResultCode(RESULT_CODE_INIT);
        refundLog.setResultMsg("退款处理中");
        refundLog.setDeleteFlag("0");
        refundLog.setVersion("1");
        refundLog.setCreateTime(LocalDateTime.now());
        refundLog.setUpdateTime(LocalDateTime.now());
        try {
            alipayRefundLogMapper.insert(refundLog);
        } catch (Exception e) {
            if (!isIntegrityViolation(e)) {
                throw e;
            }
            log.warn("退款明细插入命中唯一索引，判定为重复提交, orderNo={}, refundOrderNo={}", request.getOrderNo(), refundOrderNo);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "退款申请重复提交");
        }

        Map<String, Object> bizDataMap = new java.util.LinkedHashMap<>();
        bizDataMap.put("orderNo", request.getOrderNo());
        bizDataMap.put("cardIssueCode", "0007");
        bizDataMap.put("cardNum", cardNum);
        bizDataMap.put("channelAgreementNo", channelAgreementNo);
        bizDataMap.put("refundAmount", refundAmount);
        bizDataMap.put("refundOrderNo", refundOrderNo);

        log.info("支付宝出行-退款申请,调用支付中心退款接口,请求参数: {}", JSON.toJSONString(bizDataMap));
        PayCenterReply reply = payCenterPort.requestRefund(bizDataMap);
        log.info("支付宝退款申请,支付中心响应结果: code={}, success={}, msg={}",
                reply.code(), reply.success(), reply.msg());

        if (!(reply instanceof PayCenterReply.Accepted accepted)) {
            log.error("支付宝退款申请未拿到支付中心业务应答，退款结果未知、明细保持 PROCESSING，MUST 人工核对, orderNo={}, refundOrderNo={}, code={}, msg={}",
                    request.getOrderNo(), refundOrderNo, reply.code(), reply.msg());
            alipayRefundLogMapper.updateRefundStatus(
                    refundLog.getRefundSeq(),
                    REFUND_STATUS_PROCESSING,
                    RESULT_CODE_INIT,
                    "退款结果未知，待人工核对",
                    reply.rawBody(),
                    LocalDateTime.now()
            );
            response.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg("退款结果未知，请稍后核对");
            return response;
        }

        log.info("支付宝退款申请,支付中心解密后数据: retCode={}, retMsg={}", accepted.retCode(), accepted.retMsg());

        boolean refundSuccess = "SUCCESS".equals(accepted.retCode());
        String resultCode = refundSuccess ? FepAppErrorCodeEnum.SUCCESS.getCode() : FepAppErrorCodeEnum.FAIL.getCode();
        String resultMsg;
        if (refundSuccess) {
            resultMsg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "退款成功";
        } else {
            resultMsg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg()
                    : (StringUtils.hasText(accepted.msg()) ? accepted.msg() : "退款失败");
        }

        alipayRefundLogMapper.updateRefundStatus(
                refundLog.getRefundSeq(),
                refundSuccess ? REFUND_STATUS_SUCCESS : REFUND_STATUS_FAIL,
                resultCode,
                resultMsg,
                accepted.rawBody(),
                LocalDateTime.now()
        );

        if (refundSuccess) {
            int summaryAffected = alipayPayLogMapper.updateRefundSummary(request.getOrderNo());
            if (summaryAffected == 0) {
                log.error("退款汇总回写未命中原支付订单，MUST 人工核对 ALIPAY_PAY_LOG 与 ALIPAY_REFUND_LOG, orderNo={}, refundOrderNo={}",
                        request.getOrderNo(), refundOrderNo);
            }
        }

        response.setRetCode(resultCode);
        response.setRetMsg(resultMsg);
        log.info("支付宝退款申请完成, orderNo={}, refundOrderNo={}, cardNum={}, channelAgreementNo={}, refundAmount={}, status={}",
                request.getOrderNo(), refundOrderNo, cardNum, channelAgreementNo, refundAmount, refundSuccess ? "SUCCESS" : "FAIL");
        return response;
    }


    public AlipayCommonResponse handleRefundNotify(AlipayTripRefundNotifyReqDTO request) {
        log.info("接收到支付宝退款结果回调, 原始报文: {}", JSON.toJSONString(request));
        AlipayCommonResponse response = new AlipayCommonResponse();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            log.warn("支付宝退款回调报文为空或订单号为空");
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("参数异常：orderNo不能为空");
            return response;
        }

        recordRefundCallback(request);

        log.info("支付宝退款回调已留证据，业务回写未接线, orderNo={}, refundNo={}, outRefundNo={}, refundResult={}",
                request.getOrderNo(), request.getRefundNo(), request.getOutRefundNo(), request.getRefundResult());
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("成功");
        return response;
    }


    private void recordRefundCallback(AlipayTripRefundNotifyReqDTO request) {
        try {
            AlipayPayCallbackLog callbackLog = new AlipayPayCallbackLog();
            callbackLog.setCallbackSeq(UUID.randomUUID().toString().replaceAll("-", ""));
            callbackLog.setOrderNo(request.getOrderNo());
            callbackLog.setCallbackType(CALLBACK_TYPE_REFUND);
            callbackLog.setRefundOrderNo(request.getOutRefundNo());
            callbackLog.setRefundAmount(parseRefundAmount(request.getRefundAmount(), request.getOrderNo()));
            callbackLog.setRefundStatus(request.getRefundResult());
            callbackLog.setRawBody(JSON.toJSONString(request));
            callbackLog.setHandleStatus(HANDLE_STATUS_PENDING);
            callbackLog.setHandleMsg("退款回调回写未接线，仅留证据");
            callbackLog.setCreateTime(LocalDateTime.now());
            callbackLog.setUpdateTime(LocalDateTime.now());
            alipayPayCallbackLogMapper.insert(callbackLog);
        } catch (Exception e) {
            log.error("支付宝退款回调凭据落库失败，本次处理继续, orderNo={}", request.getOrderNo(), e);
        }
    }


    private Integer parseRefundAmount(String refundAmount, String orderNo) {
        if (!StringUtils.hasText(refundAmount)) {
            return null;
        }
        try {
            return Integer.valueOf(refundAmount.trim());
        } catch (NumberFormatException e) {
            log.warn("支付宝退款回调的退款金额不是整数分，本列留空、原文已进 RAW_BODY, orderNo={}, refundAmount={}",
                    orderNo, refundAmount);
            return null;
        }
    }


    private boolean isIntegrityViolation(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof DuplicateKeyException || current instanceof DataIntegrityViolationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}

