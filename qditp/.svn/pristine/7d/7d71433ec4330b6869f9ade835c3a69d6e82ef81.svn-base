package com.chinasofti.huateng.collectpay.entity;

import lombok.Data;

/**
 * TVM扫码充值订单表实体。
 */
@Data
public class TvmTopupOrder {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 票卡逻辑号。
     */
    private String ticketLogicNum;

    /**
     * 票卡物理号。
     */
    private String ticketPhysicsNum;

    /**
     * 充值前金额。
     */
    private String beforeAmount;

    /**
     * 请求交易金额。
     */
    private String transAmount;

    /**
     * 交易后卡内余额。
     */
    private String afterAmount;
    private String payCenterOrderNo;
    private String payCenterChannelOrderNo;

    /**
     * 订单状态：0-支付中，1-支付成功，2-支付失败，3-未支付。
     */
    private String status;
    private String msg;
    /**
     * 0：其他支付方式
     * 1：数字人民币app
     */
    private String payType;

    /**
     * 支付通道编码。
     */
    private String channel;

    /**
     * 支付URL（二维码内容）。
     */
    private String url;

    /**
     * 创建时间。
     */
    private String createTime;

    /**
     * 更新时间。
     */
    private String updateTime;

    /**
     * 设备编码。
     */
    private String deviceId;

    /**
     * 撤销操作记录ID（rsv1）。
     */
    private String rsv1;

    /**
     * 退款操作记录ID（rsv2）。
     */
    private String rsv2;

}
