package com.chinasofti.huateng.facepay.api.device.app;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF8A-20 APP 扫码取票下单入参。
 *
 * <p><b>{@code singelTicketNum} 的拼写错误是既有契约</b>（少一个 n），
 * 与 TVM 的 {@code RequestGenSjtOrderReqDTO} 同名同错，NEVER 更正。</p>
 */
public class RequestOrderReqDTO extends BaseDeviceRequest {

    /** APP 侧用户号，落到 {@code F2F_ORDER.THIRD_USER_ID}。 */
    private String userId;

    private String entryStationCode;

    private String exitStationCode;

    /** 单张票价，单位分。 */
    private String ticketPrice;

    /** 购票张数，拼写照搬。 */
    private String singelTicketNum;

    /** 0 按站点购票，其余按里程/区间，取值口径同 TVM。 */
    private String singleTicketType;

    /** @return null 表示不是合法数字 */
    public Long priceInFen() {
        return parse(ticketPrice);
    }

    /** @return null 表示不是合法数字 */
    public Integer ticketCount() {
        Long value = parse(singelTicketNum);
        return value == null || value > Integer.MAX_VALUE ? null : value.intValue();
    }

    private static Long parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public String getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(String ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getSingelTicketNum() {
        return singelTicketNum;
    }

    public void setSingelTicketNum(String singelTicketNum) {
        this.singelTicketNum = singelTicketNum;
    }

    public String getSingleTicketType() {
        return singleTicketType;
    }

    public void setSingleTicketType(String singleTicketType) {
        this.singleTicketType = singleTicketType;
    }

    @Override
    public String toString() {
        return "RequestOrderReqDTO{userId=" + userId
                + ", entryStationCode=" + entryStationCode
                + ", exitStationCode=" + exitStationCode
                + ", ticketPrice=" + ticketPrice
                + ", singelTicketNum=" + singelTicketNum
                + ", singleTicketType=" + singleTicketType + '}';
    }
}
