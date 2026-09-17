package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;
import com.chinasofti.huateng.facepay.api.device.TicketInfo;

import java.util.List;

/** IF2A-06 出票失败结果上报。 */
public class NotiTakeTicketFailResultReqDTO extends BaseDeviceRequest {

    /** 订单号。 */
    private String orderNo;

    /** 实际出票张数，可能为 0（完全没出票）。 */
    private String actualTakeTicketNum;

    /** 故障发生时间。 */
    private String faultOccurDate;

    /** TVM 打印的故障单号，乘客凭此到 BOM 处理，落 {@code F2F_RESULT_REPORT.FAULT_SLIP_SEQ}。 */
    private String faultSlipSeq;

    /** 设备侧错误码。 */
    private String errorCode;

    /** 设备侧错误描述。 */
    private String errorMessage;

    /** 已成功出票的明细，可能为空列表（一张都没出）。 */
    private List<TicketInfo> ticketList;

    /** 实际出票张数，解析失败返回 null；NEVER 把解析失败当成 0，那会退全款。 */
    public Integer actualNum() {
        if (actualTakeTicketNum == null || actualTakeTicketNum.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(actualTakeTicketNum.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getActualTakeTicketNum() {
        return actualTakeTicketNum;
    }

    public void setActualTakeTicketNum(String actualTakeTicketNum) {
        this.actualTakeTicketNum = actualTakeTicketNum;
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

    public List<TicketInfo> getTicketList() {
        return ticketList;
    }

    public void setTicketList(List<TicketInfo> ticketList) {
        this.ticketList = ticketList;
    }

    @Override
    public String toString() {
        return super.toString() + ",NotiTakeTicketFailResultReqDTO{orderNo=" + orderNo
                + ", actualTakeTicketNum=" + actualTakeTicketNum
                + ", faultSlipSeq=" + faultSlipSeq
                + ", errorCode=" + errorCode + '}';
    }
}
