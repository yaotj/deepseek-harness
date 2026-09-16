package com.chinasofti.huateng.alipay.paysign.entity;

import java.time.LocalDateTime;

/**
 * 支付宝出行支付结果回调凭据。
 *
 * <p>对应表 {@code ALIPAY_PAY_CALLBACK_LOG}，形态与 pay-sign-server 的 {@code PAY_CALLBACK_LOG} 对齐：
 * <b>回调一到就落库</b>，是支付中心推送结果的唯一凭据；后续处理失败 <b>NEVER 回滚这一行</b>，
 * 回滚等于丢证据（AGENTS.md §5.2 记录过 2026-08-26 生产事故：事务内调 RPC 被强杀后，
 * 连留证据的 INSERT 一起丢掉，循环重推 8 分钟库里零条）。</p>
 */
public class AlipayPayCallbackLog {
    private String callbackSeq;
    private String orderNo;
    private String callbackType;
    private String transStatus;
    private String payStatus;
    private String channelVoucherId;
    private String transAmount;
    private String transTime;
    private String cardNo;
    private String rawBody;
    private String handleStatus;
    private String handleMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getCallbackSeq() {
        return callbackSeq;
    }

    public void setCallbackSeq(String callbackSeq) {
        this.callbackSeq = callbackSeq;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getCallbackType() {
        return callbackType;
    }

    public void setCallbackType(String callbackType) {
        this.callbackType = callbackType;
    }

    public String getTransStatus() {
        return transStatus;
    }

    public void setTransStatus(String transStatus) {
        this.transStatus = transStatus;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public String getChannelVoucherId() {
        return channelVoucherId;
    }

    public void setChannelVoucherId(String channelVoucherId) {
        this.channelVoucherId = channelVoucherId;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getTransTime() {
        return transTime;
    }

    public void setTransTime(String transTime) {
        this.transTime = transTime;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getRawBody() {
        return rawBody;
    }

    public void setRawBody(String rawBody) {
        this.rawBody = rawBody;
    }

    public String getHandleStatus() {
        return handleStatus;
    }

    public void setHandleStatus(String handleStatus) {
        this.handleStatus = handleStatus;
    }

    public String getHandleMsg() {
        return handleMsg;
    }

    public void setHandleMsg(String handleMsg) {
        this.handleMsg = handleMsg;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
