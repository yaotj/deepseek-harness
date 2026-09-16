package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

/**
 * 单程票明细（表 F2F_TICKET）。
 *
 * <p>字段与 face-pay-server/src/main/resources/sql/f2f-schema.sql 的
 * {@code CREATE TABLE F2F_TICKET} 一一对应，改字段 MUST 同步改 DDL。
 *
 * <p>三处需要注意的映射口径：
 * <ul>
 *   <li>{@code TRANS_DATE} 是 VARCHAR2(14)（yyyyMMddHHmmss），映射为 {@code String}，
 *       <b>不是</b>时间类型。BOM 退款按「逻辑卡号 + 此字段」定位，格式不得改写。</li>
 *   <li>金额列 {@code TICKET_PRICE} 是 NUMBER(12)，用 {@code Long}（单位分），
 *       不退化成字符串。</li>
 *   <li>时间戳列是 TIMESTAMP(6)，用 {@code LocalDateTime}。</li>
 * </ul>
 *
 * <p>出票结果上报时按张写入，一次上报可能带多张票，批量落库见
 * {@code F2fTicketMapper.batchInsert}。重复上报靠
 * {@code UK_F2F_TICKET_LOGIC (TICKET_LOGIC_NUM, TRANS_DATE)} 抛
 * {@code DuplicateKeyException} 兜底，NEVER 先查后插。
 */
public class F2fTicket {

    /** 自增主键。 */
    private Long id;

    /** 所属 ITP 订单号，对应 F2F_ORDER.ORDER_NO。 */
    private String orderNo;

    /** 票卡逻辑号，下单时未知，出票上报才有。 */
    private String ticketLogicNum;

    /** 交易日期 YYYYMMDDHHMMSS，BOM 退款按逻辑号加此字段定位。 */
    private String transDate;

    /** 单张票价，单位分。 */
    private Long ticketPrice;

    /** 票状态，取值见 CK_F2F_TICKET_STATUS：ISSUED / FAULT / REFUNDING / REFUNDED。 */
    private String ticketStatus;

    /** 进站编码。 */
    private String entryStationCode;

    /** 出站编码。 */
    private String exitStationCode;

    /** 支付渠道编码。 */
    private String payChannelCode;

    /** 已退款时回填退款单号。 */
    private String refundNo;

    /** 创建时间。 */
    private LocalDateTime createTms;

    /** 更新时间。 */
    private LocalDateTime updateTms;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getTicketLogicNum() {
        return ticketLogicNum;
    }

    public void setTicketLogicNum(String ticketLogicNum) {
        this.ticketLogicNum = ticketLogicNum;
    }

    public String getTransDate() {
        return transDate;
    }

    public void setTransDate(String transDate) {
        this.transDate = transDate;
    }

    public Long getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Long ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getTicketStatus() {
        return ticketStatus;
    }

    public void setTicketStatus(String ticketStatus) {
        this.ticketStatus = ticketStatus;
    }

    public String getEntryStationCode() {
        return entryStationCode;
    }

    public void setEntryStationCode(String entryStationCode) {
        this.entryStationCode = entryStationCode;
    }

    public String getExitStationCode() {
        return exitStationCode;
    }

    public void setExitStationCode(String exitStationCode) {
        this.exitStationCode = exitStationCode;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public LocalDateTime getUpdateTms() {
        return updateTms;
    }

    public void setUpdateTms(LocalDateTime updateTms) {
        this.updateTms = updateTms;
    }
}
