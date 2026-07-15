package com.chinasofti.huateng.online.entity;

import java.time.LocalDateTime;

public class OnlineOrder {
    /**
     * 互联网业务订单主实体。
     * 该实体统一承接 TVM 售票、TVM 充值、BOM 非现金处理等多类订单，
     * 对应数据库表 ONLINE_ORDER，是 online-server 最核心的业务主表。
     */
    private String orderNo;
    /** 订单号，平台侧唯一标识一笔互联网业务订单。 */
    private String orderType;
    /** 订单类型，如 TVM_SJT、TVM_TOPUP、BOM_NOCASH。 */
    private String deviceId;
    /** 发起该订单的终端设备编码。 */
    private String entryStationCode;
    /** 起点站编码，主要用于 TVM 单程票购票场景。 */
    private String exitStationCode;
    /** 终点站编码，按站点购票时使用。 */
    private Integer ticketPrice;
    /** 单张票价，单位分。 */
    private Integer ticketNum;
    /** 票数或张数。 */
    private String singleTicketType;
    /** 单程票类型，0-按站点购票，1-固定票价购票。 */
    private String transType;
    /** BOM 交易类型，如超时更新、退票、充值、行政处理等。 */
    private String adminTransType;
    /** BOM 行政处理子类型代码。 */
    private String operatorId;
    /** 操作员编码，主要用于 BOM 业务。 */
    private String shiftId;
    /** 班次序列号。 */
    private String bomOptSeq;
    /** BOM 设备本地操作流水号。 */
    private String cardId;
    /** 逻辑卡号，二维码票/HCE 票/储值卡等统一使用该字段标识。 */
    private String ticketLogicNum;
    /** 实体票或储值票逻辑卡号，偏 TVM 充值场景。 */
    private String ticketPhysicsNum;
    /** 实体票物理号。 */
    private Integer beforeAmount;
    /** 交易前金额，充值前卡余额。 */
    private Integer transAmount;
    /** 本次交易金额，单位分。 */
    private Integer afterAmount;
    /** 交易后金额，充值后卡余额。 */
    private String payChannelCode;
    /** 支付通道编码，如 03 支付宝、04 微信。 */
    private String paymentVendor;
    /** 支付账户认证码/付款码标识。 */
    private String payResult;
    /** 支付结果：ORDERED/SUCCESS/FAILED。 */
    private String payResultDesc;
    /** 支付结果描述，给终端或日志展示。 */
    private String orderStatus;
    /** 订单业务状态，如 CREATED、PAID、FINISHED、FAILED。 */
    private String orderStatusDesc;
    /** 订单业务状态描述。 */
    private LocalDateTime qrcodeGenDate;
    /** 取票二维码生成时间，用于取票码订单反查。 */
    private String randomFact;
    /** 取票二维码随机因子。 */
    private Integer actualTakeTicketNum;
    /** 实际出票数量。 */
    private LocalDateTime takeTicketDate;
    /** 出票时间，或在部分场景下复用为处理完成时间。 */
    private String topupStatus;
    /** 充值状态：00 成功，01 失败，02 存疑，03 取消。 */
    private String businessResult;
    /** BOM 业务执行结果：SUCCESS/FAILED。 */
    private String businessResultDesc;
    /** BOM 业务执行结果描述。 */
    private LocalDateTime faultOccurDate;
    /** 故障发生时间。 */
    private String faultSlipSeq;
    /** 故障凭条号。 */
    private String errorCode;
    /** 终端回传的错误码。 */
    private String errorMessage;
    /** 终端回传的错误描述。 */
    private LocalDateTime createTms;
    /** 记录创建时间。 */
    private LocalDateTime updateTms;
    /** 记录最后更新时间。 */

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
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

    public Integer getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Integer ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public Integer getTicketNum() {
        return ticketNum;
    }

    public void setTicketNum(Integer ticketNum) {
        this.ticketNum = ticketNum;
    }

    public String getSingleTicketType() {
        return singleTicketType;
    }

    public void setSingleTicketType(String singleTicketType) {
        this.singleTicketType = singleTicketType;
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

    public String getBomOptSeq() {
        return bomOptSeq;
    }

    public void setBomOptSeq(String bomOptSeq) {
        this.bomOptSeq = bomOptSeq;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
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

    public Integer getBeforeAmount() {
        return beforeAmount;
    }

    public void setBeforeAmount(Integer beforeAmount) {
        this.beforeAmount = beforeAmount;
    }

    public Integer getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(Integer transAmount) {
        this.transAmount = transAmount;
    }

    public Integer getAfterAmount() {
        return afterAmount;
    }

    public void setAfterAmount(Integer afterAmount) {
        this.afterAmount = afterAmount;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public String getPayResult() {
        return payResult;
    }

    public void setPayResult(String payResult) {
        this.payResult = payResult;
    }

    public String getPayResultDesc() {
        return payResultDesc;
    }

    public void setPayResultDesc(String payResultDesc) {
        this.payResultDesc = payResultDesc;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getOrderStatusDesc() {
        return orderStatusDesc;
    }

    public void setOrderStatusDesc(String orderStatusDesc) {
        this.orderStatusDesc = orderStatusDesc;
    }

    public LocalDateTime getQrcodeGenDate() {
        return qrcodeGenDate;
    }

    public void setQrcodeGenDate(LocalDateTime qrcodeGenDate) {
        this.qrcodeGenDate = qrcodeGenDate;
    }

    public String getRandomFact() {
        return randomFact;
    }

    public void setRandomFact(String randomFact) {
        this.randomFact = randomFact;
    }

    public Integer getActualTakeTicketNum() {
        return actualTakeTicketNum;
    }

    public void setActualTakeTicketNum(Integer actualTakeTicketNum) {
        this.actualTakeTicketNum = actualTakeTicketNum;
    }

    public LocalDateTime getTakeTicketDate() {
        return takeTicketDate;
    }

    public void setTakeTicketDate(LocalDateTime takeTicketDate) {
        this.takeTicketDate = takeTicketDate;
    }

    public String getTopupStatus() {
        return topupStatus;
    }

    public void setTopupStatus(String topupStatus) {
        this.topupStatus = topupStatus;
    }

    public String getBusinessResult() {
        return businessResult;
    }

    public void setBusinessResult(String businessResult) {
        this.businessResult = businessResult;
    }

    public String getBusinessResultDesc() {
        return businessResultDesc;
    }

    public void setBusinessResultDesc(String businessResultDesc) {
        this.businessResultDesc = businessResultDesc;
    }

    public LocalDateTime getFaultOccurDate() {
        return faultOccurDate;
    }

    public void setFaultOccurDate(LocalDateTime faultOccurDate) {
        this.faultOccurDate = faultOccurDate;
    }

    public String getFaultSlipSeq() {
        return faultSlipSeq;
    }

    public void setFaultSlipSeq(String faultSlipSeq) {
        this.faultSlipSeq = faultSlipSeq;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
