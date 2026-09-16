package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

/**
 * IF8A-26 在线补款订单（SUPPLEMENT_ORDER），face-pay-server 为 owner。
 *
 * <p>一张补款单覆盖 N 笔未结清的 GATE_TXN_PAY 订单，明细见 {@link SupplementOrderItem}。</p>
 *
 * <p>{@code payStatus} 取值：{@code INIT} 已下单待支付 / {@code PROCESSING} 已发起支付 /
 * {@code SUCCESS} 支付成功 / {@code FAIL} 支付失败 / {@code CLOSED} 已关闭。
 * 与 GATE_TXN_PAY.DEBIT_STATUS 是两套独立状态：本状态描述补款单自身，
 * 原订单的结清与否始终以 GATE_TXN_PAY.DEBIT_STATUS 为权威口径。</p>
 *
 * <p><b>PayCenter 直连架构下的字段精简</b>：
 * CollectPay 链路的 outbox 四列（saleSyncStatus / saleSyncRetryCount / saleSyncTime / saleSyncResult）
 * 已删除——PayCenter 预下单在单次 HTTP 请求内完成，不需要「先落本地、再补偿投递」的 outbox。
 * 预下单直接写 PAYMENT_INFO / MERCHANT_ORDER_NO / PAY_CHANNEL_CODE 三列，
 * 失败则回 INIT 并记录 REMARK，后续可重试。</p>
 */
public class SupplementOrder {
    private Long id;
    /** 补款单号：SP + yyyyMMddHHmmssSSS + 卡号后6位。 */
    private String orderNo;
    private String payStatus;
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String goodsCode;
    private Integer quantity;
    /** 补款总额，单位分。 */
    private Long totalAmount;
    /** 本补款单覆盖的原过闸订单笔数。 */
    private Integer orderCount;
    private String paymentVendor;
    private String signChannelCode;
    private String requestSignSeq;
    private String txnDate;
    private LocalDateTime payTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 乘客在 APP 收银台选定的支付渠道，由 PayCenter 预下单时传入，下单时为空。 */
    private String payChannelCode;
    /** 支付中心预下单返回的商户订单号（merchantOrderNo），退款与对账要用。 */
    private String merchantOrderNo;
    /** 预下单返回的支付串，原样回给 APP 唤起收银台，本端不解析。 */
    private String paymentInfo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getPayStatus() { return payStatus; }
    public void setPayStatus(String payStatus) { this.payStatus = payStatus; }

    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }

    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }

    public String getGoodsCode() { return goodsCode; }
    public void setGoodsCode(String goodsCode) { this.goodsCode = goodsCode; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Long getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Long totalAmount) { this.totalAmount = totalAmount; }

    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }

    public String getPaymentVendor() { return paymentVendor; }
    public void setPaymentVendor(String paymentVendor) { this.paymentVendor = paymentVendor; }

    public String getSignChannelCode() { return signChannelCode; }
    public void setSignChannelCode(String signChannelCode) { this.signChannelCode = signChannelCode; }

    public String getRequestSignSeq() { return requestSignSeq; }
    public void setRequestSignSeq(String requestSignSeq) { this.requestSignSeq = requestSignSeq; }

    public String getTxnDate() { return txnDate; }
    public void setTxnDate(String txnDate) { this.txnDate = txnDate; }

    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getPayChannelCode() { return payChannelCode; }
    public void setPayChannelCode(String payChannelCode) { this.payChannelCode = payChannelCode; }

    public String getMerchantOrderNo() { return merchantOrderNo; }
    public void setMerchantOrderNo(String merchantOrderNo) { this.merchantOrderNo = merchantOrderNo; }

    public String getPaymentInfo() { return paymentInfo; }
    public void setPaymentInfo(String paymentInfo) { this.paymentInfo = paymentInfo; }

    @Override
    public String toString() {
        return "SupplementOrder{" +
                "orderNo='" + orderNo + '\'' +
                ", payStatus='" + payStatus + '\'' +
                ", thirdUserId='" + thirdUserId + '\'' +
                ", totalAmount=" + totalAmount +
                ", orderCount=" + orderCount +
                '}';
    }
}
