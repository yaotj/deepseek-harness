package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PaySignGatewayMessages {

    private PaySignGatewayMessages() {
    }

    public static Map<String, Object> buildContractBizData(RequestSignInfoReqDTO request,
                                                           String paymentVendor,
                                                           String notifyUrl) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("requestSignSeq", request.getRequestSignSeq());
        bizData.put("paymentVendor", paymentVendor);
        bizData.put("thirdUserId", request.getThirdUserId());
        bizData.put("displayAccount", request.getDisplayAccount());
        putIfHasText(bizData, "notifyUrl", notifyUrl);
        putIfHasText(bizData, "returnUrl", request.getReturnUrl());
        putIfHasText(bizData, "options", request.getOptions());
        putIfHasText(bizData, "authCode", request.getAuthCode());
        putIfHasText(bizData, "mobilePhone", request.getMobilePhone());
        putIfHasText(bizData, "certNo", request.getCertNo());
        putIfHasText(bizData, "custName", request.getCustName());
        putIfHasText(bizData, "token", request.getToken());
        putIfHasText(bizData, "payUserId", request.getPayUserId());
        putIfHasText(bizData, "bankCardNo", request.getBankCardNo());
        return bizData;
    }

    public static Map<String, Object> buildCreditQueryBizData(String thirdUserId, String requestSignSeq, String paymentVendor) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("thirdUserId", thirdUserId);
        bizData.put("requestSignSeq", requestSignSeq);
        bizData.put("paymentVendor", paymentVendor);
        return bizData;
    }

    public static Map<String, Object> buildQueryResultBizData(String requestSignSeq) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("requestSignSeq", requestSignSeq);
        return bizData;
    }

    public static Map<String, Object> buildDismissalBizData(String requestSignSeq) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("requestSignSeq", requestSignSeq);
        return bizData;
    }

    public static Map<String, Object> buildRequestPayBizData(RequestPayReqDTO request, String notifyUrl) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", request.getOrderNo());
        bizData.put("scene", request.getScene());
        bizData.put("paymentVendor", request.getPaymentVendor());
        bizData.put("amount", request.getAmount());
        bizData.put("industryType", request.getIndustryType());
        bizData.put("subject", request.getSubject());
        bizData.put("body", request.getBody());
        putIfHasText(bizData, "requestSignSeq", request.getRequestSignSeq());
        putIfHasText(bizData, "thirdUserId", request.getThirdUserId());
        putIfHasText(bizData, "industryDetail", request.getIndustryDetail());
        if (request.getOrderTimeOut() != null) {
            bizData.put("orderTimeOut", request.getOrderTimeOut());
        }
        putIfHasText(bizData, "authCode", request.getAuthCode());
        putIfHasText(bizData, "notifyUrl", notifyUrl);
        putIfHasText(bizData, "returnUrl", request.getReturnUrl());
        putIfHasText(bizData, "ipAddress", request.getIpAddress());
        putIfHasText(bizData, "remark", request.getRemark());
        return bizData;
    }


    public static Map<String, Object> buildRequestRefundBizData(PayRefundDetail refundDetail, PayTxnDetail payTxn,
                                                               String notifyUrl) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("refundOrderNo", refundDetail.getRefundOrderNo());
        bizData.put("merchantOrderNo", payTxn.getOrderNo());
        bizData.put("orderNo", payTxn.getPayCenterOrderNo());
        bizData.put("refundAmount", refundDetail.getRefundAmount());
        bizData.put("refundReason", refundDetail.getRefundReason());
        putIfHasText(bizData, "notifyUrl", notifyUrl);
        return bizData;
    }

    private static void putIfHasText(Map<String, Object> params, String key, String value) {
        if (StringUtils.hasText(value)) {
            params.put(key, value);
        }
    }
}
