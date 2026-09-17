package com.chinasofti.huateng.collectpay.model.page;

import lombok.Data;

/** TVM 当面付订单运营查询视图。 */
@Data
public class FacePayOrderPageView {
    private String orderNo;
    private String payCenterOrderNo;
    private String payCenterChannelOrderNo;
    private String status;
    private String msg;
    private String channel;
    private String payType;
    private String inStationCode;
    private String outStationCode;
    /** 起点站中文名，查询时按 IN_STATION_CODE 关联 STATION_INFO 带出。 */
    private String inStationName;
    /** 终点站中文名，查询时按 OUT_STATION_CODE 关联 STATION_INFO 带出。 */
    private String outStationName;
    private String ticketPrice;
    private Integer ticketNum;
    private String totalPrice;
    private String ticketType;
    private String deviceId;
    /** 已发起退款时记录的退款单号，来源于订单 RSV2 字段。 */
    private String refundNo;
    private String createTime;
    private String updateTime;
}
