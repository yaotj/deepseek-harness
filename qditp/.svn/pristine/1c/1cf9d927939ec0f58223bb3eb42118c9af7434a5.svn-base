package com.chinasofti.huateng.online.entity;

import java.time.LocalDateTime;

public class OnlineOrderTicket {
    /**
     * 订单票卡明细实体。
     * 用于保存一笔订单下具体写出了哪些票、每张票的交易时间与金额，
     * 主要服务于 TVM 出票结果通知和故障补传场景。
     */
    private Long id;
    /** 自增主键。 */
    private String orderNo;
    /** 关联的主订单号。 */
    private String ticketLogicNum;
    /** 票卡逻辑号。 */
    private String ticketPhysicsNum;
    /** 票卡物理号，当前主要给实体卡充值/扩展场景预留。 */
    private LocalDateTime transDate;
    /** 单张票或单次卡处理的交易时间。 */
    private Integer transAmount;
    /** 单张票或单笔卡处理的交易金额。 */
    private LocalDateTime createTms;
    /** 明细记录创建时间。 */

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

    public String getTicketPhysicsNum() {
        return ticketPhysicsNum;
    }

    public void setTicketPhysicsNum(String ticketPhysicsNum) {
        this.ticketPhysicsNum = ticketPhysicsNum;
    }

    public LocalDateTime getTransDate() {
        return transDate;
    }

    public void setTransDate(LocalDateTime transDate) {
        this.transDate = transDate;
    }

    public Integer getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(Integer transAmount) {
        this.transAmount = transAmount;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }
}
