package com.chinasofti.huateng.collectpay.model.page;

import lombok.Data;

/** BOM 非现金订单运营查询视图（TBL_BOM_ORDER_PAY）。 */
@Data
public class BomOrderPageView {
    private String orderNo;
    private String deviceId;
    /** 交易类型：01 购票发售（BomBusinessCodeEnum.SALE）/ 22 充值（BomBusinessCodeEnum.TOPUP）。 */
    private String transType;
    private String adminTransType;
    private String operaterId;
    private String shiftId;
    private String cardId;
    private String transAount;
    /** 订单支付状态：1 支付成功，其余见 TBL_BOM_ORDER_PAY.STATUS 字典。 */
    private String status;
    private String msg;
    private String paymentCode;
    private String paymentVendor;
    private String channel;
    private String payCenterOrderNo;
    private String payCenterChannelOrderNo;
    /** 已发起退款时记录的退款标记，来源于订单 RSV2 字段。 */
    private String refundNo;
    private String createTime;
    private String updateTime;
}
