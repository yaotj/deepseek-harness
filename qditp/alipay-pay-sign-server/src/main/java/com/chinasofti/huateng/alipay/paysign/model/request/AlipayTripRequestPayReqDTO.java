package com.chinasofti.huateng.alipay.paysign.model.request;

import lombok.Data;


@Data
public class AlipayTripRequestPayReqDTO {

    private String orderNo;


    private String scene;


    private String paymentVendor;


    private Integer amount;


    private String industryType;


    private String subject;


    private String body;


    private String requestSignSeq;


    private String thirdUserId;


    private Integer orderTimeOut;


    private String authCode;


    private String notifyUrl;


    private String returnUrl;


    private String ipAddress;


    private String remark;


    private String industryDetail;
}
