package com.chinasofti.huateng.model.app;

/**
 * 按卡号查询「是否仍有未结清扣费订单」的通用请求。
 *
 * <p>被两个下游共用：gate-txn-pay-server 查 {@code GATE_TXN_PAY}、
 * alipay-pay-sign-server 查 {@code ALIPAY_PAY_LOG}。故意不带模块前缀命名，
 * 新增第三个欠费源时直接复用，**NEVER** 为每个模块复制一份同结构 DTO。</p>
 *
 * <p>不带支付渠道：{@code BLACKLIST} 表只有 5 列（ID / CARD_ID / THIRD_USER_ID / REASON /
 * CREATE_TIME），既没有渠道字段，拉黑时 {@code AddBlackListReqDTO} 也不上送渠道，
 * 因此调用方拿不到 paymentVendor。黑名单本身按卡号在闸机侧生效、欠费也归属到具体卡，
 * 按 CARD_ID 判定口径正好对齐，且不受 THIRD_USER_ID 可空的影响。</p>
 */
public class CardUnsettledQueryReqDTO {

    /** 卡号。 */
    private String cardId;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }
}
