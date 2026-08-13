package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class PayLogBuilder {
    private static final Logger log = LoggerFactory.getLogger(PayLogBuilder.class);
    private static final String PAY_STATUS_FAIL = "FAIL";
    private static final String PAY_TYPE_TRIP = "TRIP";
    private static final String RESULT_CODE_INIT = "INIT";
    private static final String RESULT_MSG_INIT = "支付处理中";
    private static final String REFUND_AMOUNT_ZERO = "0";
    private static final String REFUND_STATUS_NONE = "NONE";

    public AlipayPayLog build(AlipayTripRequestPayReqDTO request, AlipaySignInfo signInfo) {
        AlipayPayLog payLog = new AlipayPayLog();
        payLog.setPaySeq(UUID.randomUUID().toString().replaceAll("-", ""));
        payLog.setThirdUserId(request.getThirdUserId());
        payLog.setCardId(signInfo.getCardId());
        payLog.setOrderNo(request.getOrderNo());
        payLog.setPayAmount(request.getAmount().toString());
        payLog.setPayStatus(PAY_STATUS_FAIL);
        payLog.setPayType(PAY_TYPE_TRIP);
        payLog.setScene(request.getScene());
        payLog.setPaymentVendor(request.getPaymentVendor());
        payLog.setIndustryType(request.getIndustryType());
        payLog.setSubject(request.getSubject());
        payLog.setBody(request.getBody());
        payLog.setRequestSignSeq(request.getRequestSignSeq());
        payLog.setOrderTimeOut(request.getOrderTimeOut() != null ? request.getOrderTimeOut().toString() : "60");
        payLog.setAuthCode(request.getAuthCode());
        payLog.setNotifyUrl(request.getNotifyUrl());
        payLog.setReturnUrl(request.getReturnUrl());
        payLog.setIpAddress(request.getIpAddress());
        payLog.setIndustryDetail(request.getIndustryDetail());
        payLog.setRequestBody(JSON.toJSONString(request));
        payLog.setResponseBody("");
        payLog.setResultCode(RESULT_CODE_INIT);
        payLog.setResultMsg(RESULT_MSG_INIT);
        payLog.setRefundAmount(REFUND_AMOUNT_ZERO);
        payLog.setRefundStatus(REFUND_STATUS_NONE);
        resolveEntryExitIds(request, payLog);
        return payLog;
    }


    private void resolveEntryExitIds(AlipayTripRequestPayReqDTO request, AlipayPayLog payLog) {
        try {
            if (request == null || payLog == null || !org.springframework.util.StringUtils.hasText(request.getIndustryDetail())) {
                return;
            }
            JSONObject detailObj = JSON.parseObject(request.getIndustryDetail());
            payLog.setEntryId(detailObj.getString("entryId"));
            payLog.setExitId(detailObj.getString("exitId"));
        } catch (Exception e) {
            log.warn("解析 industryDetail 获取 entryId/exitId 失败", e);
        }
    }
}
