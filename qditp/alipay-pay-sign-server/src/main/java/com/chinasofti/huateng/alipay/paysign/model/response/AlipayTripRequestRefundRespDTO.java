package com.chinasofti.huateng.alipay.paysign.model.response;

import lombok.Data;

/**
 * 支付宝出行-退款申请响应参数。
 */
@Data
public class AlipayTripRequestRefundRespDTO {
    /**
     * 返回码
     */
    private String retCode;

    /**
     * 返回消息
     */
    private String retMsg;
}
