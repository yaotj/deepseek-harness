package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

/**
 * 当面付退款单（表 F2F_REFUND）。
 *
 * <p>字段与 face-pay-server/src/main/resources/sql/f2f-schema.sql 一一对应，
 * 改字段 MUST 同步改 DDL。
 *
 * <p>本表的核心是防重复退款：唯一函数索引
 * {@code UK_F2F_REFUND_IDEM (ORIG_ORDER_NO, NVL(TICKET_LOGIC_NUM,'#WHOLE#'), REFUND_SOURCE)}
 * 保证「同一原订单 + 同一票 + 同一退款来源」只能有一条退款单；整单退时
 * {@code ticketLogicNum} 为空，索引用 {@code #WHOLE#} 占位，避免多笔整单退绕过约束。
 * 因此写入 MUST 直接 INSERT，NEVER 先查后插，见 {@code F2fRefundMapper} 类级说明。
 *
 * <p>与旧实体的两处刻意差异（与 {@link F2fOrder} 一致）：
 * <ul>
 *   <li>金额用 {@code Long}（单位分），不是 {@code String}。新表列是 NUMBER(12)。</li>
 *   <li>时间用 {@code LocalDateTime}，不是 {@code String}。新表列是 TIMESTAMP(6)。</li>
 * </ul>
 */
public class F2fRefund {

    /** 自增主键。 */
    private Long id;

    /** 退款单号，唯一，受 UK_F2F_REFUND_NO 约束。 */
    private String refundNo;

    /** 被退款的原 ITP 订单号。 */
    private String origOrderNo;

    /** 按票退时填；整单退时为空，唯一索引用 NVL 占位避免多笔整单退绕过约束。 */
    private String ticketLogicNum;

    /**
     * TAKE_TICKET_FAIL 出票故障自动退，BOM_ORIGINAL 单程票原路退，APP_REQUEST 用户主动退，
     * DAILY_BATCH 每日批量退未取票，TOPUP_FAIL 充值失败退，PAGE_MANUAL 运营端手工退，
     * TVM_REQUEST 设备侧 requestRefund 发起。
     */
    private String refundSource;

    /** 退款状态，取值见 CK_F2F_REFUND_STATUS：INIT / PROCESSING / SUCCESS / FAILED。 */
    private String refundStatus;

    /** 退款票数，按票退为 1，出票故障场景为未出票张数。 */
    private Integer refundNum;

    /** 退款金额，单位分；出票故障场景等于（订单张数减实际张数）乘单张票价。 */
    private Long refundAmount;

    /** 退款原因，来源于业务场景或运营端填写。 */
    private String refundReason;

    /** 发起退款的操作员号，运营端手工退与 BOM 侧有值。 */
    private String operatorId;

    /** 发起退款的设备号。 */
    private String deviceId;

    /** 原交易类型，BOM 场景透传原订单的 TRANS_TYPE。 */
    private String transType;

    /** 原交易日期 YYYYMMDDHHMMSS，BOM 按逻辑号加此字段定位原票。 */
    private String origTransDate;

    /** 支付中心侧的退款单号，退款回调按此号反查本单。 */
    private String payCenterRefundNo;

    /** 向支付中心发起退款的重试次数。 */
    private Integer retryTimes;

    /** 最近一次失败原因，重试时覆盖。 */
    private String failReason;

    /** 退款发起时间，收口放弃判定用它算已经悬了多久。 */
    private LocalDateTime requestTms;

    /**
     * 下次允许发起支付中心退款查询的时刻，扫表谓词就是它（IDX_F2F_REFUND_SCAN）。
     *
     * <p>由指数退避算出，见 {@code F2fRefundService#nextQueryTms}。
     * 扫表谓词里 <b>NEVER 再加 RETRY_TIMES 上限</b>——次数上限在 application 层判定并显式置
     * MANUAL，写进 SQL 会让次数用尽的单直接从扫描结果消失、停在非终态无人管。</p>
     */
    private LocalDateTime nextQueryTms;

    /** 退款收口时间，进入 SUCCESS / FAILED / MANUAL 终态时回填。 */
    private LocalDateTime finishTms;

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

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public String getOrigOrderNo() {
        return origOrderNo;
    }

    public void setOrigOrderNo(String origOrderNo) {
        this.origOrderNo = origOrderNo;
    }

    public String getTicketLogicNum() {
        return ticketLogicNum;
    }

    public void setTicketLogicNum(String ticketLogicNum) {
        this.ticketLogicNum = ticketLogicNum;
    }

    public String getRefundSource() {
        return refundSource;
    }

    public void setRefundSource(String refundSource) {
        this.refundSource = refundSource;
    }

    public String getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus) {
        this.refundStatus = refundStatus;
    }

    public Integer getRefundNum() {
        return refundNum;
    }

    public void setRefundNum(Integer refundNum) {
        this.refundNum = refundNum;
    }

    public Long getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(Long refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getTransType() {
        return transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }

    public String getOrigTransDate() {
        return origTransDate;
    }

    public void setOrigTransDate(String origTransDate) {
        this.origTransDate = origTransDate;
    }

    public String getPayCenterRefundNo() {
        return payCenterRefundNo;
    }

    public void setPayCenterRefundNo(String payCenterRefundNo) {
        this.payCenterRefundNo = payCenterRefundNo;
    }

    public Integer getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(Integer retryTimes) {
        this.retryTimes = retryTimes;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public LocalDateTime getRequestTms() {
        return requestTms;
    }

    public void setRequestTms(LocalDateTime requestTms) {
        this.requestTms = requestTms;
    }

    public LocalDateTime getNextQueryTms() {
        return nextQueryTms;
    }

    public void setNextQueryTms(LocalDateTime nextQueryTms) {
        this.nextQueryTms = nextQueryTms;
    }

    public LocalDateTime getFinishTms() {
        return finishTms;
    }

    public void setFinishTms(LocalDateTime finishTms) {
        this.finishTms = finishTms;
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
