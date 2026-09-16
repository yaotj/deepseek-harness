package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.entity.F2fRefund;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 一次退款请求的全部入参。用 record 是因为它<b>构造后即不可变</b>——
 * 退款金额与幂等三要素在链路中途被改写是最难查的一类缺陷。
 *
 * @param origOrderNo      原订单号，幂等三要素之一，必填
 * @param ticketLogicNum   票逻辑卡号，幂等三要素之一；<b>null 表示整单退</b>，
 *                         对应唯一索引里的 {@code #WHOLE#} 占位
 * @param refundSource     退款来源，幂等三要素之一，取值必须在 {@code CK_F2F_REFUND_SOURCE} 内
 * @param refundAmount     退款金额，单位分，必须为正（{@code CK_F2F_REFUND_AMOUNT} 也会挡）
 * @param refundNum        退票张数，整单退或充值退时可为 null
 * @param refundReason     退款原因，可空
 * @param deviceId         发起设备，可空（批量任务与运营端没有设备）
 * @param operatorId       操作员，可空
 * @param transType        原交易类型，用于对账归类
 * @param origTransDate    原交易日期，按票退款时用于定位原交易
 * @param payCenterOrderNo 支付中心侧原订单号，可空；<b>旧实现这里硬编码空串</b>
 *                         （{@code BomOrderServiceImpl:640} 留着「根据实际情况填写」的注释），
 *                         本实现要求调用方从 {@code F2F_PAYMENT.PAY_CENTER_ORDER_NO} 取真值
 */
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

    /**
     * 参数自检。<b>在落库前拦住非法值</b>，而不是等 Oracle 的 CHECK 约束抛异常——
     * 约束抛出来的是 {@code DataIntegrityViolationException}，与「已退过」的
     * {@code DuplicateKeyException} 混在一起后无法区分处理。
     *
     * @return null 表示合法；否则返回可直接回给调用方的中文原因
     */
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

    /** 组装待插入的退款单，初始状态 {@code INIT}、重试次数 0。 */
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
