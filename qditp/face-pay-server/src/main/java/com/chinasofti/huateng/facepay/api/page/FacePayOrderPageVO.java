package com.chinasofti.huateng.facepay.api.page;

/** 运营端当面付订单分页查询视图对象。 */
public class FacePayOrderPageVO {

    /** ITP 订单号。 */
    private String orderNo;

    /** 订单主状态，取 {@code F2F_ORDER.ORDER_STATUS} 原值。 */
    private String orderStatus;

    /** 受理渠道：01-APP，02-TVM，03-BOM。 */
    private String channel;

    /** 支付中心订单号，来自 {@code F2F_PAYMENT} 最后一次尝试。 */
    private String payCenterOrderNo;

    /** 渠道订单号，来自 {@code F2F_PAYMENT} 最后一次尝试。 */
    private String payCenterChannelOrderNo;

    /** 支付渠道编码，来自 {@code F2F_PAYMENT} 最后一次尝试。 */
    private String payChannelCode;

    /** 支付方式，来自 {@code F2F_PAYMENT} 最后一次尝试。 */
    private String payType;

    /** 进站编码。 */
    private String inStationCode;

    /** 出站编码。 */
    private String outStationCode;

    /** 起点站中文名，按 {@code ENTRY_STATION_CODE} 关联 {@code STATION_INFO}。 */
    private String inStationName;

    /** 终点站中文名，按 {@code EXIT_STATION_CODE} 关联 {@code STATION_INFO}。 */
    private String outStationName;

    /** 单张票价，单位分。 */
    private Long ticketPrice;

    /** 购票张数。 */
    private Integer ticketNum;

    /** 订单总额，单位分。 */
    private Long orderAmount;

    /** 0-按站点购票，1-固定票价购票。 */
    private String singleTicketType;

    /** 受理设备号。 */
    private String deviceId;

    /** 最近一笔退款单号，来自 {@code F2F_REFUND}。 */
    private String refundNo;

    /** 退款汇总状态。 */
    private String refundStatus;

    /** 已成功退款总额，单位分。 */
    private Long refundAmount;

    /** 最后一次退款汇总重算时刻。 */
    private String lastRefundTime;

    /** 创建时间，格式 {@code YYYY-MM-DD HH24:MI:SS}。 */
    private String createTime;

    /** 更新时间，格式 {@code YYYY-MM-DD HH24:MI:SS}。 */
    private String updateTime;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getPayCenterOrderNo() { return payCenterOrderNo; }
    public void setPayCenterOrderNo(String payCenterOrderNo) { this.payCenterOrderNo = payCenterOrderNo; }

    public String getPayCenterChannelOrderNo() { return payCenterChannelOrderNo; }
    public void setPayCenterChannelOrderNo(String payCenterChannelOrderNo) { this.payCenterChannelOrderNo = payCenterChannelOrderNo; }

    public String getPayChannelCode() { return payChannelCode; }
    public void setPayChannelCode(String payChannelCode) { this.payChannelCode = payChannelCode; }

    public String getPayType() { return payType; }
    public void setPayType(String payType) { this.payType = payType; }

    public String getInStationCode() { return inStationCode; }
    public void setInStationCode(String inStationCode) { this.inStationCode = inStationCode; }

    public String getOutStationCode() { return outStationCode; }
    public void setOutStationCode(String outStationCode) { this.outStationCode = outStationCode; }

    public String getInStationName() { return inStationName; }
    public void setInStationName(String inStationName) { this.inStationName = inStationName; }

    public String getOutStationName() { return outStationName; }
    public void setOutStationName(String outStationName) { this.outStationName = outStationName; }

    public Long getTicketPrice() { return ticketPrice; }
    public void setTicketPrice(Long ticketPrice) { this.ticketPrice = ticketPrice; }

    public Integer getTicketNum() { return ticketNum; }
    public void setTicketNum(Integer ticketNum) { this.ticketNum = ticketNum; }

    public Long getOrderAmount() { return orderAmount; }
    public void setOrderAmount(Long orderAmount) { this.orderAmount = orderAmount; }

    public String getSingleTicketType() { return singleTicketType; }
    public void setSingleTicketType(String singleTicketType) { this.singleTicketType = singleTicketType; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }

    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }

    public Long getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Long refundAmount) { this.refundAmount = refundAmount; }

    public String getLastRefundTime() { return lastRefundTime; }
    public void setLastRefundTime(String lastRefundTime) { this.lastRefundTime = lastRefundTime; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }

    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}
