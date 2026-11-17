package com.chinasofti.huateng.alipay.paysign.model.request;

import lombok.Data;


@Data
public class AlipayTripAddContractReqDTO {
    private String thirdUserId;
    private String channel;
    private String agreementCode;
    private String channelAgreementCode;
    private String channelUserAccount;
    private String cardIssueCode;
}
