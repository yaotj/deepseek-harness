package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.entity.F2fRefund;

import java.time.LocalDateTime;
import java.util.Set;

public record RefundCommand(String origOrderNo,
                            String ticketLogicNum,
                            String refundSource,
                            long refundAmount,
                            Integer refundNum,
                            String refundReason,
                            String deviceId,
                            String operatorId,
                            String transType,
                            String origTransDate,
                            String payCenterOrderNo) {

    private static final Set<String> ALLOWED_SOURCES = Set.of(
            F2fRefundService.SOURCE_TAKE_TICKET_FAIL,
            F2fRefundService.SOURCE_BOM_ORIGINAL,
            F2fRefundService.SOURCE_APP_REQUEST,
            F2fRefundService.SOURCE_DAILY_BATCH,
            F2fRefundService.SOURCE_TOPUP_FAIL,
            F2fRefundService.SOURCE_PAGE_MANUAL,
            F2fRefundService.SOURCE_TVM_REQUEST);

    public String validate() {
        if (origOrderNo == null || origOrderNo.isBlank()) {
            return "原订单号不能为空";
        }
        if (refundAmount <= 0) {
            return "退款金额必须为正数";
        }
        if (refundSource == null || !ALLOWED_SOURCES.contains(refundSource)) {
            return "退款来源非法: " + refundSource;
        }
        if (refundNum != null && refundNum <= 0) {
            return "退票张数必须为正数";
        }
        return null;
    }

    public F2fRefund toEntity(String refundNo, LocalDateTime now) {
        F2fRefund refund = new F2fRefund();
        refund.setRefundNo(refundNo);
        refund.setOrigOrderNo(origOrderNo);
        refund.setTicketLogicNum(ticketLogicNum);
        refund.setRefundSource(refundSource);
        refund.setRefundStatus(F2fRefundService.STATUS_INIT);
        refund.setRefundNum(refundNum);
        refund.setRefundAmount(refundAmount);
        refund.setRefundReason(refundReason);
        refund.setOperatorId(operatorId);
        refund.setDeviceId(deviceId);
        refund.setTransType(transType);
        refund.setOrigTransDate(origTransDate);
        refund.setRetryTimes(0);
        refund.setRequestTms(now);
        refund.setCreateTms(now);
        refund.setUpdateTms(now);
        return refund;
    }

    @Override
    public String toString() {
        return "RefundCommand{origOrderNo=" + origOrderNo
                + ", ticketLogicNum=" + ticketLogicNum
                + ", refundSource=" + refundSource
                + ", refundAmount=" + refundAmount
                + ", refundNum=" + refundNum + '}';
    }
}
