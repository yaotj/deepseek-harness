package com.chinasofti.huateng.alipay.paysign.model.response;

import lombok.Data;


@Data
public class AlipayTripPayQueryRespDTO {

    private String retCode;


    private String retMsg;


    private String outTradeNo;


    private String paymentTime;


    private String tradeStatus;


    private String totalAmount;


    private String tradeNo;


    private String tradeDesc;
}
