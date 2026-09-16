package com.chinasofti.huateng.facepay.api.page;

/**
 * 运营端当面付订单分页查询视图对象。
 *
 * <p>字段名与取值直接对齐 {@code F2F_ORDER} 域模型，
 * <b>不再做旧口径的状态映射或格式变换</b>：
 * <ul>
 *   <li>{@code orderStatus} 存真实枚举值（CREATED / PAYING / PAID / FULFILLED / REFUNDED 等），
 *       不再有旧 {@code status="1"} 这种 ItpStatusEnum 投影。</li>
 *   <li>{@code orderAmount} / {@code ticketPrice} / {@code refundAmount} 为 {@link Long} 单位分，
 *       不再输出旧 {@code TO_CHAR} 字符串。</li>
 *   <li>{@code singleTicketType} 直接投影 {@code F2F_ORDER.SINGLE_TICKET_TYPE}，
 *       不再是旧 {@code ticketType}。</li>
 *   <li>已退款状态由 {@link #refundStatus}（NONE / PARTIAL / SUCCESS）独立承载，
 *       与 {@code orderStatus} 正交（ADR-D88）。</li>
 * </ul>
 *
 * <p>为什么不沿用 collect-pay-server 的 {@code FacePayOrderPageView}：那个类
 * 是旧表的单行结构投影（旧单表把支付中心单号、退款单号都塞在一行里），拆成
 * F2F_ORDER + F2F_PAYMENT + F2F_REFUND 之后字段来源和口径都变了，硬套旧 View
 * 只会让两套命名在前端打架。</p>
 */
public class FacePayOrderPageVO {

    /** ITP 订单号。 */
    private String orderNo;

    /**
     * 订单主状态，取 {@code F2F_ORDER.ORDER_STATUS} 原值。
     * 值域见 {@code CK_F2F_ORDER_STATUS}：CREATED / PAYING / PAID / FULFILLED /
     * FULFILL_FAILED / TOPUP_SUSPECT / PAY_FAILED / EXPIRED / REFUNDING / REFUNDED / CANCELED。
     */
    private String orderStatus;

    /** 受理渠道：01-APP，02-TVM，03-BOM。 */
    private String channel;

    /** 支付中心订单号，来自 {@code F2F_PAYMENT} 最后一次尝试。 */
    private String payCenterOrderNo;

    /** 渠道订单号，来自 {@code F2F_PAYMENT} 最后一次尝试。 */
    private String payCenterChannelOrderNo;

    /** 支付渠道编码，来自 {@code F2F_PAYMENT} 最后一次尝试。 */
    private String payChannelCode;

    /** 支付方式，来自 {@code F2F_PAYMENT} 最后一次尝试。BOM 柜台售票无值。 */
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

    /** 0-按站点购票，1-固定票价购票。直接投影 {@code F2F_ORDER.SINGLE_TICKET_TYPE}。 */
    private String singleTicketType;

    /** 受理设备号。 */
    private String deviceId;

    /** 最近一笔退款单号，来自 {@code F2F_REFUND}。整单或部分退各可能有多行，取最新 ID。 */
    private String refundNo;

    /**
     * 退款汇总状态。值域见 {@code CK_F2F_ORDER_REFUND_STATUS}：NONE / PARTIAL / SUCCESS。
     * 与 {@link #orderStatus} 正交，退款 NEVER 改主状态。
     */
    private String refundStatus;

    /** 已成功退款总额，单位分。由 {@code F2F_REFUND} 中 SUCCESS 的行重算得出。 */
    private Long refundAmount;

    /** 最后一次退款汇总重算时刻。格式 {@code YYYY-MM-DD HH24:MI:SS}。 */
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
