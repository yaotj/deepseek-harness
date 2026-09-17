package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** IF2A-11 写卡充值失败结果上报。 */
public class TopupCardFailNotiReqDTO extends BaseDeviceRequest {

    /** 充值失败：需要退款。 */
    public static final String STATUS_FAILED = "01";

    /** 订单号。 */
    private String orderNo;

    /** 票卡逻辑卡号。 */
    private String ticketLogicNum;

    /** 票卡物理卡号。 */
    private String ticketPhysicsNum;

    /** 充值结果：{@code 01} 失败 / {@code 02} 存疑 / {@code 03} 取消。 */
    private String topupStatus;

    /** 故障发生时间。 */
    private String faultOccurDate;

    /** TVM 打印的故障单号。 */
    private String faultSlipSeq;

    /** 设备侧错误码。 */
    private String errorCode;

    /** 设备侧错误描述。 */
    private String errorMessage;

    /** 是否需要触发退款：仅 {@code topupStatus=01}。 */
    public boolean needRefund() {
        return STATUS_FAILED.equals(topupStatus);
    }

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

    public String getTopupStatus() {
        return topupStatus;
    }

    public void setTopupStatus(String topupStatus) {
        this.topupStatus = topupStatus;
    }

    public String getFaultOccurDate() {
        return faultOccurDate;
    }

    public void setFaultOccurDate(String faultOccurDate) {
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

    @Override
    public String toString() {
        return super.toString() + ",TopupCardFailNotiReqDTO{orderNo=" + orderNo
                + ", ticketLogicNum=" + ticketLogicNum
                + ", topupStatus=" + topupStatus
                + ", faultSlipSeq=" + faultSlipSeq
                + ", errorCode=" + errorCode + '}';
    }
}
