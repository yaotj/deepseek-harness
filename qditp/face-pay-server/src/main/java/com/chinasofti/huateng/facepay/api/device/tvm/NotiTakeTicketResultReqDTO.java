package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;
import com.chinasofti.huateng.facepay.api.device.TicketInfo;

import java.util.List;

/** IF2A-05 出票成功结果上报。 */
public class NotiTakeTicketResultReqDTO extends BaseDeviceRequest {

    /** 订单号。 */
    private String orderNo;

    /** 实际出票张数。 */
    private String actualTakeTicketNum;

    /** 出票时间。 */
    private String takeTickeDate;

    /** 本次出票的票明细，可能为空列表（设备只报张数不报明细时）。 */
    private List<TicketInfo> ticketList;

    /** 实际出票张数，解析失败返回 null。 */
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

    public String getTakeTickeDate() {
        return takeTickeDate;
    }

    public void setTakeTickeDate(String takeTickeDate) {
        this.takeTickeDate = takeTickeDate;
    }

    public List<TicketInfo> getTicketList() {
        return ticketList;
    }

    public void setTicketList(List<TicketInfo> ticketList) {
        this.ticketList = ticketList;
    }

    @Override
    public String toString() {
        return super.toString() + ",NotiTakeTicketResultReqDTO{orderNo=" + orderNo
                + ", actualTakeTicketNum=" + actualTakeTicketNum
                + ", takeTickeDate=" + takeTickeDate
                + ", ticketList=" + ticketList + '}';
    }
}
