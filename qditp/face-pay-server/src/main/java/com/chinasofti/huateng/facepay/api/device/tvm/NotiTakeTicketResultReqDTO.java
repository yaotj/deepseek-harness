package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;
import com.chinasofti.huateng.facepay.api.device.TicketInfo;

import java.util.List;

/**
 * IF2A-05 出票成功结果上报。
 * 对应 {@code POST /itptvm/ci/tvm/notiTakeTicketResult} 的 {@code bizData}。
 *
 * <p><b>{@code takeTickeDate} 的拼写错误（少一个 t）是既有契约，NEVER 改正</b>——
 * 设备侧按这个 key 发报文，改名等于收不到值。</p>
 */
public class NotiTakeTicketResultReqDTO extends BaseDeviceRequest {

    /** 订单号。必填，为空时返回 retCode=2002。 */
    private String orderNo;

    /** 实际出票张数。设备传字符串，旧实现直接 parseInt 无保护，本实现用 {@link #actualNum()} 兜底。 */
    private String actualTakeTicketNum;

    /** 出票时间。<b>字段名少一个 t 是既有契约</b>。 */
    private String takeTickeDate;

    /** 本次出票的票明细，可能为空列表（设备只报张数不报明细时）。 */
    private List<TicketInfo> ticketList;

    /**
     * 实际出票张数，解析失败返回 null。
     *
     * <p>旧实现是 {@code Integer.parseInt(...)} 裸调用，非数字或为空即抛异常并退化成
     * 全局异常处理器的 UUID retCode，设备会一直重传。这里返回 null 让调用方按 2002 拒绝。</p>
     */
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
