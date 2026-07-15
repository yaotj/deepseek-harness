package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * IF8A-10 计算票价响应参数。
 */
public class RequestTicketPriceByStationResult extends CommonResult {
    /** 票价，单位按参数表票价字段定义。 */
    private String ticketPrice;

    public String getTicketPrice() { return ticketPrice; }
    public void setTicketPrice(String ticketPrice) { this.ticketPrice = ticketPrice; }
}
