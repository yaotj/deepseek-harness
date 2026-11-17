package com.chinasofti.huateng.alipay.paysign.model.request;

import lombok.Data;


@Data
public class AlipayTripPayQueryReqDTO {

    private String orderNo;


    private String cardIssueCode;


    private String cardNum;


    private String channelAgreementNo;
}
