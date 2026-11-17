package com.chinasofti.huateng.alipay.paysign.model.response;

import lombok.Data;


@Data
public class AlipayTripTerminateContractRespDTO {
    private String retCode;
    private String retMsg;
    private String agreementCode;
}
