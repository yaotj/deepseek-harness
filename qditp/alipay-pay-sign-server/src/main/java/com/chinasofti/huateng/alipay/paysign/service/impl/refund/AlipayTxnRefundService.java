package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.AlipayPayCenterMsgLogWriter;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;


@Service
public class AlipayTxnRefundService {

    private static final Logger log = LoggerFactory.getLogger(AlipayTxnRefundService.class);

    private static final String CARD_ISSUE_CODE_ALIPAY = "0007";
    private static final String PAY_STATUS_SUCCESS = "SUCCESS";
    private static final String API_NAME_REQUEST_REFUND = "requestRefund";


    private static final String REFUND_STATUS_INIT = "INIT";
    private static final String REFUND_STATUS_PROCESSING = "PROCESSING";

    private final AlipayPayTxnDetailMapper alipayPayTxnDetailMapper;
    private final AlipayRefundTxnDetailMapper alipayRefundTxnDetailMapper;
    private final AlipaySignInfoMapper alipaySignInfoMapper;
    private final PayCenterPort payCenterPort;
    private final PayCenterProperties payCenterProperties;
    private final AlipayPayCenterMsgLogWriter payCenterMsgLogWriter;

    public AlipayTxnRefundService(AlipayPayTxnDetailMapper alipayPayTxnDetailMapper,
                                 AlipayRefundTxnDetailMapper alipayRefundTxnDetailMapper,
                                 AlipaySignInfoMapper alipaySignInfoMapper,
                                 PayCenterPort payCenterPort,
                                 PayCenterProperties payCenterProperties,
                                 AlipayPayCenterMsgLogWriter payCenterMsgLogWriter) {
        this.alipayPayTxnDetailMapper = alipayPayTxnDetailMapper;
        this.alipayRefundTxnDetailMapper = alipayRefundTxnDetailMapper;
        this.alipaySignInfoMapper = alipaySignInfoMapper;
        this.payCenterPort = payCenterPort;
        this.payCenterProperties = payCenterProperties;
        this.payCenterMsgLogWriter = payCenterMsgLogWriter;
    }

    public AlipayTripTxnRefundRespDTO requestRefund(AlipayTripTxnRefundReqDTO request) {
        log.info("接收到支付宝退款申请报文（新表链路）: {}", JSON.toJSONString(request));
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号不能为空");
        }
        String orderNo = request.getOrderNo().trim();

        AlipayPayTxnDetail payTxn = loadSettledPayTxn(orderNo);
        rejectIfProcessingRefundExists(orderNo);

        String channelAgreementNo = payTxn.getChannelAgreementNo();
        if (!StringUtils.hasText(channelAgreementNo)) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录缺少渠道协议号");
        }
        String cardNum = resolveCardNum(channelAgreementNo);
        int refundAmount = resolveRefundAmount(request.getRefundAmount(), payTxn);

        String refundOrderNo = "R" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        String txnDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        openRefund(orderNo, refundOrderNo, txnDate, refundAmount, request.getRefundReason());

        return callPayCenter(orderNo, refundOrderNo, txnDate, cardNum, channelAgreementNo, refundAmount);
    }


    private AlipayPayTxnDetail loadSettledPayTxn(String orderNo) {
        AlipayPayTxnDetail payTxn = alipayPayTxnDetailMapper.selectByOrderNo(orderNo);
        if (payTxn == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录不存在");
        }
        if (!PAY_STATUS_SUCCESS.equals(payTxn.getPayStatus())) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付订单未支付成功");
        }
        return payTxn;
    }

    private void rejectIfProcessingRefundExists(String orderNo) {
        int processingCount = alipayRefundTxnDetailMapper.countByOrderNoAndStatus(orderNo, REFUND_STATUS_PROCESSING);
        if (processingCount > 0) {
            log.error("该订单存在未收口的退款明细，拒绝重复退款，MUST 人工到支付中心核对上一笔结果, orderNo={}, processingCount={}",
                    orderNo, processingCount);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "该订单存在处理中的退款，请先确认上一笔结果");
        }
    }


    private String resolveCardNum(String channelAgreementNo) {
        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByChannelAgreementCode(channelAgreementNo);
        String cardNum = signInfo != null ? signInfo.getCardId() : null;
        if (!StringUtils.hasText(cardNum)) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(),
                    "退款申请未查到签约信息, channelAgreementNo=" + channelAgreementNo);
        }
        return cardNum;
    }


    private int resolveRefundAmount(String requestRefundAmount, AlipayPayTxnDetail payTxn) {
        int paid = payTxn.getAmount() != null ? payTxn.getAmount() : 0;
        int refunded = payTxn.getRefundAmount() != null ? payTxn.getRefundAmount() : 0;
        int available = paid - refunded;
        if (available <= 0) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(),
                    "原支付订单已无可退金额, orderNo=" + payTxn.getOrderNo());
        }
        if (!StringUtils.hasText(requestRefundAmount)) {
            return available;
        }
        int current;
        try {
            current = Integer.parseInt(requestRefundAmount.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(),
                    "退款金额格式异常: " + requestRefundAmount);
        }
        if (current <= 0) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "退款金额必须大于0");
        }
        if (current > available) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "退款金额超出可退金额");
        }
        return current;
    }


    private void openRefund(String orderNo, String refundOrderNo, String txnDate, int refundAmount,
                            String refundReason) {
        AlipayRefundTxnDetail record = new AlipayRefundTxnDetail();
        record.setRefundOrderNo(refundOrderNo);
        record.setOrderNo(orderNo);
        record.setRefundStatus(REFUND_STATUS_INIT);
        record.setRefundAmount(refundAmount);
        record.setRefundReason(refundReason);
        record.setMerchantRefundNo(refundOrderNo);
        record.setRequestCount(0);
        record.setTxnDate(txnDate);
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            alipayRefundTxnDetailMapper.insert(record);
        } catch (RuntimeException e) {
            if (!RefundLogRepository.isIntegrityViolation(e)) {
                throw e;
            }
            log.warn("退款明细插入命中唯一索引，判定为重复提交, orderNo={}, refundOrderNo={}", orderNo, refundOrderNo);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "退款申请重复提交");
        }
        int marked = alipayRefundTxnDetailMapper.markRequesting(refundOrderNo, txnDate);
        if (marked == 0) {
            log.error("退款明细刚插入却标记不到，MUST 人工核对 ALIPAY_REFUND_TXN_DETAIL, orderNo={}, refundOrderNo={}, txnDate={}",
                    orderNo, refundOrderNo, txnDate);
            throw new BusinessException(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "退款明细留痕失败，请稍后重试");
        }
    }


    private AlipayTripTxnRefundRespDTO callPayCenter(String orderNo, String refundOrderNo, String txnDate,
                                                    String cardNum, String channelAgreementNo, int refundAmount) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", orderNo);
        bizData.put("cardIssueCode", CARD_ISSUE_CODE_ALIPAY);
        bizData.put("cardNum", cardNum);
        bizData.put("channelAgreementNo", channelAgreementNo);
        bizData.put("refundAmount", String.valueOf(refundAmount));
        bizData.put("refundOrderNo", refundOrderNo);
        String refundNotifyUrl = payCenterProperties.getRefundNotifyUrl();
        if (StringUtils.hasText(refundNotifyUrl)) {
            bizData.put("notifyUrl", refundNotifyUrl);
        } else {
            log.warn("pay.center.refund-notify-url 未配置，本次退款申请不送 notifyUrl，支付中心将无法回推退款结果，MUST 配置后重试, orderNo={}, refundOrderNo={}",
                    orderNo, refundOrderNo);
        }

        log.info("支付宝出行-退款申请（新表链路）,调用支付中心退款接口,请求参数: {}", JSON.toJSONString(bizData));
        long startedAt = System.currentTimeMillis();
        PayCenterReply reply;
        try {
            reply = payCenterPort.requestRefund(bizData);
        } catch (RuntimeException e) {
            payCenterMsgLogWriter.record(orderNo, txnDate, API_NAME_REQUEST_REFUND, refundOrderNo, bizData, null,
                    System.currentTimeMillis() - startedAt, "退款申请出网异常: " + e.getClass().getSimpleName());
            throw e;
        }
        long elapsedMs = System.currentTimeMillis() - startedAt;
        payCenterMsgLogWriter.record(orderNo, txnDate, API_NAME_REQUEST_REFUND, refundOrderNo, bizData, reply,
                elapsedMs, null);
        log.info("支付宝退款申请（新表链路）,支付中心响应结果: code={}, success={}, msg={}",
                reply.code(), reply.success(), reply.msg());

        AlipayTripTxnRefundRespDTO response = applyReply(reply, orderNo, refundOrderNo, txnDate);
        log.info("支付宝退款申请完成（新表链路）, orderNo={}, refundOrderNo={}, cardNum={}, channelAgreementNo={}, refundAmount={}, retCode={}",
                orderNo, refundOrderNo, cardNum, channelAgreementNo, refundAmount, response.getRetCode());
        return response;
    }


    private AlipayTripTxnRefundRespDTO applyReply(PayCenterReply reply, String orderNo, String refundOrderNo,
                                                  String txnDate) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                log.info("支付宝退款申请（新表链路）已受理,支付中心应答: orderNo={}, refundOrderNo={}, code={}, msg={}",
                        orderNo, refundOrderNo, accepted.code(), accepted.msg());
                acceptRefund(accepted, refundOrderNo, txnDate);
                return response(FepAppErrorCodeEnum.SUCCESS.getCode(), "退款申请已受理", refundOrderNo);
            }
            case PayCenterReply.Rejected rejected -> {
                return keepProcessing(orderNo, refundOrderNo, txnDate, rejected.code(), rejected.msg());
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                return keepProcessing(orderNo, refundOrderNo, txnDate, noAnswer.code(), noAnswer.msg());
            }
        }
    }


    private void acceptRefund(PayCenterReply.Accepted accepted, String refundOrderNo, String txnDate) {
        AlipayRefundTxnDetail record = new AlipayRefundTxnDetail();
        record.setRefundOrderNo(refundOrderNo);
        record.setTxnDate(txnDate);
        record.setRefundStatus(REFUND_STATUS_PROCESSING);
        record.setMerchantRefundNo(accepted.field("merchantRefundNo") != null
                ? accepted.field("merchantRefundNo") : refundOrderNo);
        record.setRefundNo(accepted.field("refundNo"));
        record.setChannelRefundNo(accepted.field("channelRefundNo"));
        record.setRefundTime(accepted.field("refundTime"));
        record.setRemark(truncate("退款申请已受理: " + accepted.msg()));
        int affected = alipayRefundTxnDetailMapper.updateRequestResult(record);
        if (affected == 0) {
            log.error("退款明细回写未命中，MUST 人工核对 ALIPAY_REFUND_TXN_DETAIL, refundOrderNo={}, txnDate={}",
                    refundOrderNo, txnDate);
        }
    }

    private AlipayTripTxnRefundRespDTO keepProcessing(String orderNo, String refundOrderNo, String txnDate,
                                                     Integer code, String msg) {
        log.error("支付宝退款申请未拿到支付中心业务应答，退款结果未知、明细保持 PROCESSING，MUST 人工核对, orderNo={}, refundOrderNo={}, code={}, msg={}",
                orderNo, refundOrderNo, code, msg);
        writeResult(refundOrderNo, txnDate, REFUND_STATUS_PROCESSING, "退款结果未知，待人工核对: code=" + code);
        return response(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "退款结果未知，请稍后核对", refundOrderNo);
    }

    private void writeResult(String refundOrderNo, String txnDate, String refundStatus, String remark) {
        AlipayRefundTxnDetail record = new AlipayRefundTxnDetail();
        record.setRefundOrderNo(refundOrderNo);
        record.setTxnDate(txnDate);
        record.setRefundStatus(refundStatus);
        record.setRemark(truncate(remark));
        int affected = alipayRefundTxnDetailMapper.updateRequestResult(record);
        if (affected == 0) {
            log.error("退款明细回写未命中，MUST 人工核对 ALIPAY_REFUND_TXN_DETAIL, refundOrderNo={}, txnDate={}, refundStatus={}",
                    refundOrderNo, txnDate, refundStatus);
        }
    }

    private String truncate(String remark) {
        if (remark == null || remark.length() <= 512) {
            return remark;
        }
        return remark.substring(0, 512);
    }

    private AlipayTripTxnRefundRespDTO response(String retCode, String retMsg, String refundOrderNo) {
        AlipayTripTxnRefundRespDTO response = new AlipayTripTxnRefundRespDTO();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        response.setRefundOrderNo(refundOrderNo);
        return response;
    }
}
