package com.chinasofti.huateng.alipay.paysign.model.response;

import lombok.Data;

/**
 * 支付宝出行-解约登记响应参数。
 */
@Data
public class AlipayTripTerminateContractRespDTO {
    private String retCode;
    private String retMsg;
    private String agreementCode;
}
