package com.chinasofti.huateng.alipay.paysign.model.request;

import lombok.Data;

/**
 * 支付宝出行-解约登记请求参数。
 */
@Data
public class AlipayTripTerminateContractReqDTO {
    private String agreementCode;
    private String merchantNo;
}
