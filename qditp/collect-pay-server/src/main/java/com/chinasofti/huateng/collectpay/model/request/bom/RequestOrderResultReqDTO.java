package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/**
 * IF2A-09 BOM 订单结果查询请求 DTO。
 * BOM 按逻辑卡号 + 交易日期查询对应出票信息和支付状态。
 */
public class RequestOrderResultReqDTO extends BaseRequestDTO {

    /**
     * 逻辑卡号。
     */
    private String ticketLogicNum;

    /**
     * 交易日期（格式：YYYYMMDD）。
     */
    private String transDate;

    public String getTicketLogicNum() {
        return ticketLogicNum;
    }

    public void setTicketLogicNum(String ticketLogicNum) {
        this.ticketLogicNum = ticketLogicNum;
    }

    public String getTransDate() {
        return transDate;
    }

    public void setTransDate(String transDate) {
        this.transDate = transDate;
    }
}
