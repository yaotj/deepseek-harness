package com.chinasofti.huateng.collectpay.model.request.tvm;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import com.chinasofti.huateng.collectpay.model.request.PayCenterBaseRequestDTO;
import lombok.Data;

/**
 * 支付结果回调
 */
@Data
public class PayNoticeReqDTO extends PayCenterBaseRequestDTO {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 商户订单号。
     */
    private String merchantOrderNo;

    /**
     * 渠道订单号
     */
    private String channelOrderNo;

    /**
     * 交易状态
     */
    private String status;

    /**
     * 支付时间（格式：yyyyMMddHHmmss）
     */
    private String payTime;
    /**
     * 订单总金额（分）
     */
    private String totalAmount;
    /**
     * 实付金额（分）
     */
    private String cashAmount;
    /**
     * 优惠金额（分）
     */
    private String couponAmount;
    /**
     * 支付渠道账户（买家账户）
     */
    private String payUserId;
    /**
     * 支付方式
     */
    private String paymentVendor;
    /**
     * 附加参数
     */
    private String options;

}
