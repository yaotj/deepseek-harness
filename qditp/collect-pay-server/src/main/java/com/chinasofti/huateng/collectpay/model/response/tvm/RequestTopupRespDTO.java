package com.chinasofti.huateng.collectpay.model.response.tvm;

import lombok.Data;

/**
 * IF2A-09 请求充值下单响应参数。
 */
@Data
public class RequestTopupRespDTO {
    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 二维码信息（支付URL）。
     */
    private String payUrl;
}
