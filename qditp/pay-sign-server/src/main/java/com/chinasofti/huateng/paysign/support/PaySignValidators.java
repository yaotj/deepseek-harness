package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import org.springframework.util.StringUtils;

public final class PaySignValidators {

    private PaySignValidators() {
    }

    public static String validateRequestSignInfo(RequestSignInfoReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getDisplayAccount())) {
            return "displayAccount不能为空";
        }
        if (!StringUtils.hasText(request.getPayChannelCode())) {
            return "payChannelCode不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        return null;
    }

    public static String validateContractQuery(String thirdUserId, String requestSignSeq, String paymentVendor) {
        if (!StringUtils.hasText(thirdUserId)) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(requestSignSeq)) {
            return "requestSignSeq不能为空";
        }
        if (!StringUtils.hasText(paymentVendor)) {
            return "paymentVendor不能为空";
        }
        return null;
    }

    public static String validateRequestTermination(RequestTerminationReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        return null;
    }

    public static String validateRequestPay(RequestPayReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        if (!StringUtils.hasText(request.getScene())) {
            return "scene不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (request.getAmount() == null) {
            return "amount不能为空";
        }
        if (request.getAmount() < 0) {
            return "amount不能小于0";
        }
        if (!StringUtils.hasText(request.getIndustryType())) {
            return "industryType不能为空";
        }
        if (!StringUtils.hasText(request.getSubject())) {
            return "subject不能为空";
        }
        if (!StringUtils.hasText(request.getBody())) {
            return "body不能为空";
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        return null;
    }

    public static String validateRequestRefund(RequestRefundReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        if (request.getRefundAmount() == null) {
            return "refundAmount不能为空";
        }
        if (request.getRefundAmount() <= 0) {
            return "refundAmount必须大于0";
        }
        return null;
    }

    public static String validateReceiveSignResult(ReceiveSignResultReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        if (!StringUtils.hasText(request.getPaymentVendor())) {
            return "paymentVendor不能为空";
        }
        if (!StringUtils.hasText(request.getStatus())) {
            return "status不能为空";
        }
        return null;
    }

    public static String validateReceiveTerminationResult(ReceiveTerminationResultReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        if (!StringUtils.hasText(request.getPaymentVendor())) {
            return "paymentVendor不能为空";
        }
        if (!StringUtils.hasText(request.getStatus())) {
            return "status不能为空";
        }
        return null;
    }
}
