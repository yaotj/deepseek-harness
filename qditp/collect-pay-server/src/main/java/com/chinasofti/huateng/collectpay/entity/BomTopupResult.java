package com.chinasofti.huateng.collectpay.entity;

/** BOM充值结果通知实体类（BOM_TOPUP_RESULT）。 */
public class BomTopupResult {

    /** 订单号。 */
    private String orderNo;

    /** 票卡逻辑卡号。 */
    private String ticketLogicNum;

    /** 票卡物理卡号。 */
    private String ticketPhysicsNum;

    /** 交易日期。 */
    private String transDate;

    /** 交易金额。 */
    private String transAmount;

    /** 余额。 */
    private String afterAmount;

    /** 充值状态：00=成功，01=失败。 */
    private String topupStatus;

    /** 交易类型：01=充值。 */
    private String transType;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
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

    public String getTransDate() {
        return transDate;
    }

    public void setTransDate(String transDate) {
        this.transDate = transDate;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getAfterAmount() {
        return afterAmount;
    }

    public void setAfterAmount(String afterAmount) {
        this.afterAmount = afterAmount;
    }

    public String getTopupStatus() {
        return topupStatus;
    }

    public void setTopupStatus(String topupStatus) {
        this.topupStatus = topupStatus;
    }

    public String getTransType() {
        return transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }
}
