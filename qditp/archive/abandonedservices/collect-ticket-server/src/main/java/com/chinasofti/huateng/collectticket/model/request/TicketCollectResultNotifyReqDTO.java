package com.chinasofti.huateng.collectticket.model.request;

/**
 * IF2A-04 取票结果通知（ITP通知APP）。
 */
public class TicketCollectResultNotifyReqDTO {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 用户ID。
     */
    private String userId;

    /**
     * 取票状态：100-取票成功，1-99-取票失败。
     */
    private Integer collectStatus;

    /**
     * 实际出票数量。
     */
    private Integer actualTakeTicketNum;

    /**
     * 故障凭证号。
     */
    private String faultSlipSeq;

    /**
     * 错误代码：2101-取票二维码超时等。
     */
    private String errorCode;

    /**
     * 错误详细信息。
     */
    private String errorMessage;

    /**
     * 通知时间。
     */
    private String notifyTime;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getCollectStatus() {
        return collectStatus;
    }

    public void setCollectStatus(Integer collectStatus) {
        this.collectStatus = collectStatus;
    }

    public Integer getActualTakeTicketNum() {
        return actualTakeTicketNum;
    }

    public void setActualTakeTicketNum(Integer actualTakeTicketNum) {
        this.actualTakeTicketNum = actualTakeTicketNum;
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

    public String getNotifyTime() {
        return notifyTime;
    }

    public void setNotifyTime(String notifyTime) {
        this.notifyTime = notifyTime;
    }

    @Override
    public String toString() {
        return "TicketCollectResultNotifyReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", userId='" + userId + '\'' +
                ", collectStatus=" + collectStatus +
                ", actualTakeTicketNum=" + actualTakeTicketNum +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}
