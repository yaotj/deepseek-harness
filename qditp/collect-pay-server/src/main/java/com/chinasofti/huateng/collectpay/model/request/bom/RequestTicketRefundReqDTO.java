package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import lombok.Data;

@Data
public class RequestTicketRefundReqDTO extends BaseRequestDTO {


    private String orderNo;
    private String TakeTicketNum;
    private String ticketRefundDate;
    private String ticketLogicNum;
    private String transDate;
    private String transAmount;
    private String transType;
    // 回填
    private String refundNo;

}
