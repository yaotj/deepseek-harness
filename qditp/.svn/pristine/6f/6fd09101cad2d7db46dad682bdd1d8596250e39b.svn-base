package com.chinasofti.huateng.collectpay.entity;

import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * TVM APP下单订单实体类。
 * 对应数据库表TBL_TVM_APP_ORDER，存储APP下单业务的订单信息。
 */
@Data
@Component
public class TvmAppOrder {

    /**
     * 订单号（主键）。
     */
    private String orderNo;

    /**
     * 用户编码。
     */
    private String userId;

    /**
     * 起点站点代码。
     */
    private String inStationCode;

    /**
     * 终点站点代码。
     */
    private String outStationCode;

    /**
     * 票价，单位：分。
     */
    private String ticketPrice;

    /**
     * 购买数量。
     */
    private String ticketNum;
    /**
     * 总价
     */
    private String totalPrice;

    /**
     * 购票类型：0-有起点站和终点站。
     */
    private String ticketType;

    /**
     * 支付状态。
     * 0-未支付，1-支付成功，2-支付失败，3-支付中。
     */
    private String payStatus;
    private String msg;
    /**
     * 发起支付标志 0-未发起 1-已发起
     */
    private String requestPayFlag;



    /**
     * 支付通道编码。
     */
    private String payChannelCode;
    private String payCenterOrderNo;
    private String payCenterChannelOrderNo;

    /**
     * 支付交易流水号。
     */
    private String merchantOrderNo;

    /**
     * 支付金额。
     */
    private String payAmount;

    /**
     * 支付时间。
     */
    private String payTime;
    private String paymentInfo;

    /**
     * 激活标志。
     * 0-未激活，1-已激活。
     */
    private String activateFlag;
    private String deviceId;
    private String qrcodeGenDate;
    private String randomFact;
    private String activeTime;

    /**
     * 取票凭证（用于生成二维码）。
     */
    private String voucher;


    /**
     * 签名类型。
     * 00：不签名，01：sha1withrsa，02：MD5。
     */
    private String signType;

    /**
     * 签名值。
     */
    private String sign;

    /**
     * 创建时间。
     */
    private String createTime;

    /**
     * 更新时间。
     */
    private String updateTime;

    /**
     * 预留字段1。
     */
    private String rsv1;

    /**
     * 预留字段2。
     */
    private String rsv2;


}