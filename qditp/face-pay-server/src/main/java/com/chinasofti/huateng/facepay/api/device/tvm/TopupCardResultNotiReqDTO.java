package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF2A-10 写卡充值成功结果上报。
 * 对应 {@code POST /itptvm/ci/tvm/topupCardResultNoti} 的 {@code bizData}。
 *
 * <p><b>补上的旧缺口</b>：旧实现只往通知表插一行，<b>从不回写主单</b>——
 * 现成的 {@code UpdateDbMap.getTopupNotiUpdateSuccessDb} 全仓库无调用点，
 * 结果 {@code AFTER_AMOUNT} 恒空、卡内余额与订单表长期不一致、对账无据。
 * 本实现把 {@code afterAmount} 回写到 {@code F2F_ORDER.CARD_AFTER_AMOUNT}，
 * 并把订单推进到 {@code FULFILLED}。</p>
 */
public class TopupCardResultNotiReqDTO extends BaseDeviceRequest {

    /** 订单号。必填。 */
    private String orderNo;

    /** 票卡逻辑卡号。必填，service 内会与订单上的卡号比对，不一致即拒绝。 */
    private String ticketLogicNum;

    /** 票卡物理卡号。必填。 */
    private String ticketPhysicsNum;

    /** 写卡交易时间。 */
    private String transDate;

    /** 本次充值金额，单位分。 */
    private String transAmount;

    /** 充值后卡内余额，单位分。回写 {@code F2F_ORDER.CARD_AFTER_AMOUNT}。 */
    private String afterAmount;

    /** 充值金额转 {@code Long}（分），解析失败返回 null。 */
    public Long transAmountInFen() {
        return parse(transAmount);
    }

    /** 充值后余额转 {@code Long}（分），解析失败返回 null（此时不回写余额列）。 */
    public Long afterAmountInFen() {
        return parse(afterAmount);
    }

    private static Long parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getTicketLogicNum() {
        return ticketLogicNum;
    }

    public void setTicketLogicNum(String ticketLogicNum) {
        this.ticketLogicNum = ticketLogicNum;
    }

    public String getTicketPhysicsNum() {
        return ticketPhysicsNum;
    }

    public void setTicketPhysicsNum(String ticketPhysicsNum) {
        this.ticketPhysicsNum = ticketPhysicsNum;
    }

    public String getTransDate() {
        return transDate;
    }

    public void setTransDate(String transDate) {
        this.transDate = transDate;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getAfterAmount() {
        return afterAmount;
    }

    public void setAfterAmount(String afterAmount) {
        this.afterAmount = afterAmount;
    }

    @Override
    public String toString() {
        return super.toString() + ",TopupCardResultNotiReqDTO{orderNo=" + orderNo
                + ", ticketLogicNum=" + ticketLogicNum
                + ", transAmount=" + transAmount
                + ", afterAmount=" + afterAmount + '}';
    }
}
