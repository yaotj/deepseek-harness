package com.chinasofti.huateng.alipay.paysign.model.request;

import lombok.Data;


@Data
public class AlipayTripTerminateContractReqDTO {
    private String agreementCode;
    private String merchantNo;
}
