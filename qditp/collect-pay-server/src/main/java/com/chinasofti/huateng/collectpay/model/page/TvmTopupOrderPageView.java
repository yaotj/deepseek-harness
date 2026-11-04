package com.chinasofti.huateng.collectpay.model.page;

import lombok.Data;

/** TVM 充值订单运营查询视图（TBL_TVM_ORDER_TOPUP ＋ 最新一条 TBL_TVM_Topup_Notiy 充值回执态）。 */
@Data
public class TvmTopupOrderPageView {
    private String orderNo;
    private String ticketLogicNum;
    private String ticketPhysicsNum;
    private String beforeAmount;
    private String transAmount;
    private String afterAmount;
    /** 订单支付状态：1 支付成功，其余见 TBL_TVM_ORDER_TOPUP.STATUS 字典。 */
    private String status;
    private String msg;
    private String channel;
    private String payType;
    private String payCenterOrderNo;
    private String payCenterChannelOrderNo;
    private String deviceId;
    /** 最新充值回执状态：00 成功 / 01 失败 / 02 存疑 / 03 取消，无回执为 null（待充值）。 */
    private String topupStatus;
    /** 已发起退款时记录的退款单号，来源于订单 RSV2 字段。 */
    private String refundNo;
    private String createTime;
    private String updateTime;
}
