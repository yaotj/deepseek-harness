package com.chinasofti.huateng.collectpay.entity;

import java.time.LocalDateTime;

/**
 * TVM出票主记录表实体（tbl_tvm_main_ticket）。
 */
public class TvmMainTicket {
    /**
     * 主键ID。
     */
    private String id;

    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 实际出票数量。
     */
    private Integer actualTakeTicketNum;

    /**
     * 出票时间/故障时间（格式：YYYYMMDDHHMMSS）。
     */
    private String takeTickeDate;

    /**
     * 通知类型：0-出票结果通知，1-出票故障通知。
     */
    private String notifyType;

    /**
     * 故障凭条号。
     */
    private String faultSlipSeq;

    /**
     * 错误代码。
     */
    private String errorCode;

    /**
     * 执行错误信息。
     */
    private String errorMessage;

    /**
     * 购票数量。
     */
    private Integer buyTicketNum;



    /**
     * 创建时间。
     */
    private String createTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getActualTakeTicketNum() {
        return actualTakeTicketNum;
    }

    public void setActualTakeTicketNum(Integer actualTakeTicketNum) {
        this.actualTakeTicketNum = actualTakeTicketNum;
    }

    public Integer getBuyTicketNum() {
        return buyTicketNum;
    }

    public void setBuyTicketNum(Integer buyTicketNum) {
        this.buyTicketNum = buyTicketNum;
    }


    public String getTakeTickeDate() {
        return takeTickeDate;
    }

    public void setTakeTickeDate(String takeTickeDate) {
        this.takeTickeDate = takeTickeDate;
    }

    public String getNotifyType() {
        return notifyType;
    }

    public void setNotifyType(String notifyType) {
        this.notifyType = notifyType;
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

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "TvmMainTicket{" +
                "id=" + id +
                ", orderNo='" + orderNo + '\'' +
                ", actualTakeTicketNum=" + actualTakeTicketNum +
                ", buyTicketNum=" + buyTicketNum +
                ", takeTickeDate='" + takeTickeDate + '\'' +
                ", notifyType=" + notifyType +
                ", faultSlipSeq='" + faultSlipSeq + '\'' +
                ", errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
