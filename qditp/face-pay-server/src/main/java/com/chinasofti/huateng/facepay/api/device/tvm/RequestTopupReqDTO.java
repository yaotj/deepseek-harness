package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF2A-09 票卡充值下单请求报文。
 * 对应 {@code POST /itptvm/ci/tvm/requestTopup} 的 {@code bizData}。
 *
 * <p><b>修掉的旧缺陷</b>：旧实现校验里没有 {@code payType}，随后却直接
 * {@code request.getPayType().equals("0")}，缺字段即 NPE。本实现把 {@code payType}
 * 纳入必填校验，且判断写成 {@code "0".equals(payType)} 的顺序。</p>
 *
 * <p>{@code payType=0} 走本地聚合码（不调支付中心），其余走支付中心预下单，
 * 与购票链路的口径一致。</p>
 */
public class RequestTopupReqDTO extends BaseDeviceRequest {

    /** 票卡逻辑卡号。必填。 */
    private String ticketLogicNum;

    /** 票卡物理卡号。必填。 */
    private String ticketPhysicsNum;

    /** 充值前卡内余额，单位分。必填。 */
    private String beforeAmount;

    /** 本次充值金额，单位分。必填。 */
    private String transAmount;

    /** 支付方式：{@code 0} 聚合码，其余走支付中心。必填（旧实现漏校验导致 NPE）。 */
    private String payType;

    /**
     * 充值金额转 {@code Long}（分）；为空或非数字返回 null。
     *
     * <p>负数与 0 <b>能解析出来</b>，由 {@code F2fTopupService.requestTopup} 的
     * {@code transAmount <= 0} 分支单独回「transAmount必须为正数」，与「不是合法数字」区分开。</p>
     *
     * <p>非数字返回 null、上层按 {@code 2002} 拒绝是<b>有意收紧</b>：旧实现对
     * {@code transAmount="abc"} 直接返回 {@code 0000} 并建单（2026-09-11 新旧双打实测），
     * 金额根本没落地却告诉设备成功，NEVER 退回旧行为。</p>
     */
    public Long transAmountInFen() {
        return parse(transAmount);
    }

    /**
     * 充值前余额转 {@code Long}（分）；为空、非数字或负数返回 null。
     *
     * <p><b>{@code 0} 是合法值</b>——新卡 / 已刷空的卡余额就是 0，此时必须能充值。
     * 本方法曾与 {@link #transAmountInFen()} 共用「必须为正」的解析，导致
     * {@code beforeAmount="0"} 被判成非法数字、整笔充值被 {@code 2002} 拒绝，
     * 而旧实现返回 {@code 0000} + payUrl（2026-09-11 新旧双打实测）。
     * 两个字段的合法区间不同，NEVER 再合并成同一个 parse。</p>
     */
    public Long beforeAmountInFen() {
        Long parsed = parse(beforeAmount);
        return parsed != null && parsed >= 0 ? parsed : null;
    }

    private static Long parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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

    public String getBeforeAmount() {
        return beforeAmount;
    }

    public void setBeforeAmount(String beforeAmount) {
        this.beforeAmount = beforeAmount;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    @Override
    public String toString() {
        return super.toString() + ",RequestTopupReqDTO{ticketLogicNum=" + ticketLogicNum
                + ", ticketPhysicsNum=" + ticketPhysicsNum
                + ", beforeAmount=" + beforeAmount
                + ", transAmount=" + transAmount
                + ", payType=" + payType + '}';
    }
}
