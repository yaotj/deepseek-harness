package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

/** 出向通知任务（表 F2F_NOTIFY_TASK）。 */
public class F2fNotifyTask {

    /** 自增主键。 */
    private Long id;

    /** 通知类型，取值见 CK_F2F_NOTIFY_TYPE：TAKE_TICKET_OK / TAKE_TICKET_FAIL / REFUND_RESULT / PAY_RESULT。 */
    private String notifyType;

    /** 通知目标，当前仅 APP。 */
    private String target;

    /** 关联的 ITP 订单号，幂等键第二段。 */
    private String orderNo;

    /** 关联的退款单号，退款类通知才有值；幂等键内用 NVL 占位为 '#NONE#'。 */
    private String refundNo;

    /** GIVEUP 表示超过最大重试次数放弃，需人工介入，不再扫表。 */
    private String notifyStatus;

    /** 通知报文体，落库后原样投递，重试不再重新组装。 */
    private String payload;

    /** 已重试次数，每次投递失败加 1。 */
    private Integer retryTimes;

    /** 最大重试次数，达到即置 GIVEUP，DDL 默认 5。 */
    private Integer maxRetryTimes;

    /** 下次重试时间，退避策略由应用计算后写入，扫表只按此字段取。 */
    private LocalDateTime nextRetryTms;

    /** 最近一次失败原因，供人工排查 GIVEUP 任务。 */
    private String lastError;

    /** 投递成功时间。 */
    private LocalDateTime successTms;

    /** 创建时间。 */
    private LocalDateTime createTms;

    /** 更新时间。 */
    private LocalDateTime updateTms;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNotifyType() {
        return notifyType;
    }

    public void setNotifyType(String notifyType) {
        this.notifyType = notifyType;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public String getNotifyStatus() {
        return notifyStatus;
    }

    public void setNotifyStatus(String notifyStatus) {
        this.notifyStatus = notifyStatus;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
    public Integer getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(Integer retryTimes) {
        this.retryTimes = retryTimes;
    }

    public Integer getMaxRetryTimes() {
        return maxRetryTimes;
    }

    public void setMaxRetryTimes(Integer maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
    }

    public LocalDateTime getNextRetryTms() {
        return nextRetryTms;
    }

    public void setNextRetryTms(LocalDateTime nextRetryTms) {
        this.nextRetryTms = nextRetryTms;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getSuccessTms() {
        return successTms;
    }

    public void setSuccessTms(LocalDateTime successTms) {
        this.successTms = successTms;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public LocalDateTime getUpdateTms() {
        return updateTms;
    }

    public void setUpdateTms(LocalDateTime updateTms) {
        this.updateTms = updateTms;
    }
}
