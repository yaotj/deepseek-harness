package com.chinasofti.huateng.alipay.paysign.model.request;

import lombok.Data;

/**
 * 支付宝出行-添加签约信息请求参数。
 */
@Data
public class AlipayTripAddContractReqDTO {
    private String thirdUserId;
    private String channel;
    private String agreementCode;
    private String channelAgreementCode;
    private String channelUserAccount;
    private String cardIssueCode;
}
