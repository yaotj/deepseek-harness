package com.chinasofti.huateng.alipay.paysign.model.request;

import lombok.Data;


@Data
public class AlipayTripRequestRefundReqDTO {

    private String orderNo;


    private String refundAmount;
}
