package com.chinasofti.huateng.collectpay.model.request.app;

import lombok.Data;

/**
 * 5.2 退款回调 支付中心回调itp
 */
@Data
public class NoticeAppTakeTicketDTO {

    private String orderNo;
    private String orderTicketNum;
    private String actualTakeTicketNum;
    private String takeTickeDate;

}
