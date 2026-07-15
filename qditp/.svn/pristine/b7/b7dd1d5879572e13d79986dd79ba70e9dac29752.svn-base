package com.chinasofti.huateng.alipay.paysign.model.request;

import lombok.Data;

/**
 * 支付宝出行-支付申请请求参数。
 */
@Data
public class AlipayTripRequestPayReqDTO {
    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 支付类型/场景
     */
    private String scene;

    /**
     * 支付方式
     */
    private String paymentVendor;

    /**
     * 支付金额（单位：分）
     */
    private Integer amount;

    /**
     * 行业类型：1-地铁 2-公交 3-打车 4-购物
     */
    private String industryType;

    /**
     * 订单标题
     */
    private String subject;

    /**
     * 订单描述
     */
    private String body;

    /**
     * 签约流水号（免密场景必填）
     */
    private String requestSignSeq;

    /**
     * 用户ID
     */
    private String thirdUserId;

    /**
     * 订单超时时间（秒），默认60秒
     */
    private Integer orderTimeOut;

    /**
     * 授权码（部分渠道主动支付需要）
     */
    private String authCode;

    /**
     * 回调地址
     */
    private String notifyUrl;

    /**
     * 返回前端页面地址（可提前配置）
     */
    private String returnUrl;

    /**
     * 用户IP地址
     */
    private String ipAddress;

    /**
     * 备注
     */
    private String remark;

    /**
     * 行业详情，json格式
     */
    private String industryDetail;
}
