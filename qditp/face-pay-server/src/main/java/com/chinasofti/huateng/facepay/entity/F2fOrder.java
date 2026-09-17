package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

/** 当面付收款单（表 F2F_ORDER）。 */
public class F2fOrder {

    /** 自增主键。 */
    private Long id;

    /** ITP 订单号，含版本标识位 F2。 */
    private String orderNo;

    /** 受理渠道：01-APP，02-TVM，03-BOM。 */
    private String channel;

    /** 业务类型：01-购票，02-充值，03-取票，04-非现金收款。 */
    private String bizType;

    /** BOM 交易类型，规格九个取值见 DDL 注释；代码只对 42 做校验。 */
    private String transType;

    /** 行政交易类型，transType=42 时必填。 */
    private String adminTransType;

    /** 内部状态机，取值见 CK_F2F_ORDER_STATUS。 */
    private String orderStatus;

    /** 订单总金额，单位分。 */
    private Long orderAmount;

    /** 退款汇总状态：{@code NONE} / {@code PARTIAL} / {@code SUCCESS}，取值见 CK_F2F_ORDER_REFUND_STATUS。 */
    private String refundStatus;

    /** 已成功退款总额，单位分。 */
    private Long refundAmount;

    /** 最后一次退款汇总重算时刻。 */
    private LocalDateTime lastRefundTms;

    /** 受理设备号。 */
    private String deviceId;

    /** 终端设备流水号。 */
    private String deviceSeq;

    /** 车站编码。 */
    private String stationCode;

    /** 操作员号，BOM 侧有值。 */
    private String operatorId;

    /** 班次号，BOM 侧有值。 */
    private String shiftId;

    /** 第三方用户标识，APP 侧有值。 */
    private String thirdUserId;

    /** 逻辑卡号。 */
    private String cardId;

    /** 购票张数。 */
    private Integer ticketNum;

    /** 单张票价，单位分。 */
    private Long ticketPrice;

    /** 0-按站点购票，1-按固定票价购票。 */
    private String singleTicketType;

    /** 进站编码。 */
    private String entryStationCode;

    /** 出站编码。 */
    private String exitStationCode;

    /** 充值前卡内余额，单位分。 */
    private Long cardBeforeAmount;

    /** 充值后卡内余额，单位分。 */
    private Long cardAfterAmount;

    /** TVM 充值存疑时打印的故障单号，BOM 凭此号查询。 */
    private String suspectSlipNo;

    /** 取票二维码生成时间 yyyyMMddHHmmss。 */
    private String qrcodeGenDate;

    /** 取票二维码随机因子。 */
    private String randomFact;

    /** 0-未激活，1-已激活。 */
    private String activateFlag;

    /** 取票订单已被哪台设备激活。 */
    private String activateDeviceId;

    /** 激活时间。 */
    private LocalDateTime activateTms;

    /** 二维码失效时间，创建时间加 180 秒。 */
    private LocalDateTime expireTms;

    /** 支付成功时间。 */
    private LocalDateTime paidTms;

    /** 业务完成（出票 / 充值成功）时间。 */
    private LocalDateTime fulfillTms;

    /** 创建时间，分区键。 */
    private LocalDateTime createTms;

    /** 更新时间。 */
    private LocalDateTime updateTms;

    /** 备注，承载旧表 MSG 列的内容。 */
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getTransType() {
        return transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }

    public String getAdminTransType() {
        return adminTransType;
    }

    public void setAdminTransType(String adminTransType) {
        this.adminTransType = adminTransType;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public Long getOrderAmount() {
        return orderAmount;
    }

    public void setOrderAmount(Long orderAmount) {
        this.orderAmount = orderAmount;
    }

    public String getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus) {
        this.refundStatus = refundStatus;
    }

    public Long getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(Long refundAmount) {
        this.refundAmount = refundAmount;
    }

    public LocalDateTime getLastRefundTms() {
        return lastRefundTms;
    }

    public void setLastRefundTms(LocalDateTime lastRefundTms) {
        this.lastRefundTms = lastRefundTms;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceSeq() {
        return deviceSeq;
    }

    public void setDeviceSeq(String deviceSeq) {
        this.deviceSeq = deviceSeq;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getShiftId() {
        return shiftId;
    }

    public void setShiftId(String shiftId) {
        this.shiftId = shiftId;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public Integer getTicketNum() {
        return ticketNum;
    }

    public void setTicketNum(Integer ticketNum) {
        this.ticketNum = ticketNum;
    }

    public Long getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Long ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getSingleTicketType() {
        return singleTicketType;
    }

    public void setSingleTicketType(String singleTicketType) {
        this.singleTicketType = singleTicketType;
    }

    public String getEntryStationCode() {
        return entryStationCode;
    }

    public void setEntryStationCode(String entryStationCode) {
        this.entryStationCode = entryStationCode;
    }

    public String getExitStationCode() {
        return exitStationCode;
    }

    public void setExitStationCode(String exitStationCode) {
        this.exitStationCode = exitStationCode;
    }

    public Long getCardBeforeAmount() {
        return cardBeforeAmount;
    }

    public void setCardBeforeAmount(Long cardBeforeAmount) {
        this.cardBeforeAmount = cardBeforeAmount;
    }

    public Long getCardAfterAmount() {
        return cardAfterAmount;
    }

    public void setCardAfterAmount(Long cardAfterAmount) {
        this.cardAfterAmount = cardAfterAmount;
    }

    public String getSuspectSlipNo() {
        return suspectSlipNo;
    }

    public void setSuspectSlipNo(String suspectSlipNo) {
        this.suspectSlipNo = suspectSlipNo;
    }

    public String getQrcodeGenDate() {
        return qrcodeGenDate;
    }

    public void setQrcodeGenDate(String qrcodeGenDate) {
        this.qrcodeGenDate = qrcodeGenDate;
    }

    public String getRandomFact() {
        return randomFact;
    }

    public void setRandomFact(String randomFact) {
        this.randomFact = randomFact;
    }

    public String getActivateFlag() {
        return activateFlag;
    }

    public void setActivateFlag(String activateFlag) {
        this.activateFlag = activateFlag;
    }

    public String getActivateDeviceId() {
        return activateDeviceId;
    }

    public void setActivateDeviceId(String activateDeviceId) {
        this.activateDeviceId = activateDeviceId;
    }

    public LocalDateTime getActivateTms() {
        return activateTms;
    }

    public void setActivateTms(LocalDateTime activateTms) {
        this.activateTms = activateTms;
    }

    public LocalDateTime getExpireTms() {
        return expireTms;
    }

    public void setExpireTms(LocalDateTime expireTms) {
        this.expireTms = expireTms;
    }

    public LocalDateTime getPaidTms() {
        return paidTms;
    }

    public void setPaidTms(LocalDateTime paidTms) {
        this.paidTms = paidTms;
    }

    public LocalDateTime getFulfillTms() {
        return fulfillTms;
    }

    public void setFulfillTms(LocalDateTime fulfillTms) {
        this.fulfillTms = fulfillTms;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
