package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import org.springframework.util.StringUtils;

/** 入向报文的**必填字段校验**，返回第一条不满足的说明、全部通过返回 {@code null}（2026-09-15 拆分批次 2）。 */
public final class PaySignValidators {

    private PaySignValidators() {
    }

    /** IF8A-16 请求签约信息。 */
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

    /** 签约查询类接口（IF8A-21 / IF8A-22）的公共参数。 */
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

    /** IF8A-06 请求解约。 */
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

    /** IF8A-19 免密扣款。 */
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

    /** 请求退款。金额上界（可退金额）不在这里判，见类注释。 */
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

    /** 签约结果回调。 */
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

    /** 解约结果回调（IF8B-02）。thirdUserId / cardId / cardType 同样由调用方补齐，不在此必填。 */
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
