package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;


public class RequestTicketPriceByStationResult extends CommonResult {
    private String ticketPrice;

    public String getTicketPrice() { return ticketPrice; }
    public void setTicketPrice(String ticketPrice) { this.ticketPrice = ticketPrice; }
}
