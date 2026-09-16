package com.chinasofti.huateng.model.pay;

/**
 * IF8A-26 请求补款下单应答报文。
 *
 * <p>规格《青岛地铁-ITP与APP接口规范R6》§3.35 表74 只有三个字段：
 * {@code retCode} / {@code retMsg} / {@code orderNo}（补款单号）。</p>
 *
 * <p>{@code payStatus} 与 {@code totalAmount} 是我方补充的返回字段，便于 APP 在重复下单时
 * 判断拿到的是新建单还是既有未支付单，**不在甲方规格内**。若甲方明确要求应答不得多返回字段，
 * 需按 {@code docs/business/ride-code.md} 中 AGM 应答那条约束的思路裁掉——但本接口是 APP 方向，
 * APP 侧 JSON 解析不会因未知字段报错。</p>
 */
public class SupplementOrderRespDTO {
    /** 返回码，0000 表示补款单已生成。 */
    private String retCode;
    /** 返回消息。 */
    private String retMsg;
    /** 补款单号，SP 前缀，同时作为后续调支付接口的 orderNo。 */
    private String orderNo;
    /** 补款单支付状态：INIT / PROCESSING / SUCCESS / FAIL / CLOSED。 */
    private String payStatus;
    /** 补款单总金额，单位分。 */
    private Long totalAmount;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
    }

    @Override
    public String toString() {
        return "SupplementOrderRespDTO{" +
                "retCode='" + retCode + '\'' +
                ", retMsg='" + retMsg + '\'' +
                ", orderNo='" + orderNo + '\'' +
                ", payStatus='" + payStatus + '\'' +
                ", totalAmount=" + totalAmount +
                '}';
    }
}
