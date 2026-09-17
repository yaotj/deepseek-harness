package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** 发往**支付中心网关**的 6 组 {@code bizData} 组装（2026-09-15 拆分批次 3）。 */
public final class PaySignGatewayMessages {

    private PaySignGatewayMessages() {
    }

    /** IF8A-16 正式签约（contract）。{@code notifyUrl} 由调用方按「请求值 → 配置默认 → returnUrl」解析后传入。 */
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

    /** IF8A-21 授信查询（creditQuery）。 */
    public static Map<String, Object> buildCreditQueryBizData(String thirdUserId, String requestSignSeq, String paymentVendor) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("thirdUserId", thirdUserId);
        bizData.put("requestSignSeq", requestSignSeq);
        bizData.put("paymentVendor", paymentVendor);
        return bizData;
    }

    /** IF8A-22 签约结果查询（queryResult）。 */
    public static Map<String, Object> buildQueryResultBizData(String requestSignSeq) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("requestSignSeq", requestSignSeq);
        return bizData;
    }

    /** IF8A-06 请求解约（dismissal）。 */
    public static Map<String, Object> buildDismissalBizData(String requestSignSeq) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("requestSignSeq", requestSignSeq);
        return bizData;
    }

    /** IF8A-19 免密扣款（requestPay）。{@code notifyUrl} 由调用方按「请求值 → 支付专用配置」解析后传入。 */
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

    /** 请求退款（requestRefund）。{@code orderNo} 取原支付的 {@code PAY_CENTER_ORDER_NO}，见类注释。 */
    public static Map<String, Object> buildRequestRefundBizData(PayRefundDetail refundDetail, PayTxnDetail payTxn) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("refundOrderNo", refundDetail.getRefundOrderNo());
        bizData.put("merchantOrderNo", payTxn.getOrderNo());
        bizData.put("orderNo", payTxn.getPayCenterOrderNo());
        bizData.put("refundAmount", refundDetail.getRefundAmount());
        bizData.put("refundReason", refundDetail.getRefundReason());
        return bizData;
    }

    /** 空值字段一律**不进报文**，而不是进报文后取值为 null。 */
    private static void putIfHasText(Map<String, Object> params, String key, String value) {
        if (StringUtils.hasText(value)) {
            params.put(key, value);
        }
    }
}
