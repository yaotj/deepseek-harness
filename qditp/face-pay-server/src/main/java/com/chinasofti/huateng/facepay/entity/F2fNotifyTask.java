package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

/**
 * 出向通知任务（表 F2F_NOTIFY_TASK）。
 *
 * <p>字段与 face-pay-server/src/main/resources/sql/f2f-schema.sql 一一对应，
 * 改字段 MUST 同步改 DDL。
 *
 * <p>本项目不使用消息队列，出向通知统一是「落库状态 + {@code @Scheduled} 扫表重试」，
 * 因此本表是通知可靠投递的唯一载体，替代旧实现的三张 tbl_notice_app_* 表，
 * 对应 IF8B-04/05/06/07。三条本表特有的约束：
 * <ul>
 *   <li><b>幂等靠函数唯一索引 UK_F2F_NOTIFY_IDEM</b>
 *       {@code (NOTIFY_TYPE, ORDER_NO, NVL(REFUND_NO,'#NONE#'))}：
 *       同类型同订单同退款单只投递一次。落库 MUST 直接 INSERT，
 *       NEVER 先查后插，重复由 {@code DuplicateKeyException} 兜底。</li>
 *   <li><b>退避策略由应用算好 {@code nextRetryTms} 后写入</b>，SQL 内不做任何时间计算，
 *       扫表只按 IDX_F2F_NOTIFY_SCAN {@code (NOTIFY_STATUS, NEXT_RETRY_TMS)} 取。</li>
 *   <li><b>{@code notifyStatus} 取值 PENDING / SUCCESS / FAILED / GIVEUP</b>，
 *       其中 GIVEUP 表示超过最大重试次数放弃、需人工介入、不再被扫表捞出。</li>
 * </ul>
 */
public class F2fNotifyTask {

    /** 自增主键。 */
    private Long id;

    /** 通知类型，取值见 CK_F2F_NOTIFY_TYPE：TAKE_TICKET_OK / TAKE_TICKET_FAIL / REFUND_RESULT / PAY_RESULT。 */
    private String notifyType;

    /**
     * 通知目标，当前仅 APP。旧实现三张表均为 tbl_notice_app_*，无向 BOM / TVM 的出向通知；
     * 新增目标 MUST 先确认对端有接收接口，NEVER 先放开 CHECK 再找场景。
     */
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
