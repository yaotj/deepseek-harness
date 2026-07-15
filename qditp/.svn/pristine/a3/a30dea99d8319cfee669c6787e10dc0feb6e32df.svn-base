package com.chinasofti.huateng.collectpay.entity;

/**
 * TVM出票明细记录表实体（tbl_tvm_sub_ticket）。
 */
public class TvmSubTicket {
    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 主表ID（外键关联tbl_tvm_main_ticket）。
     */
    private String mainTicketId;

    /**
     * 票卡逻辑号。
     */
    private String ticketLogicNum;

    /**
     * 交易日期（格式：YYYYMMDDHHMMSS）。
     */
    private String transDate;

    /**
     * 交易金额。
     */
    private String transAmount;
    private String createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMainTicketId() {
        return mainTicketId;
    }

    public void setMainTicketId(String mainTicketId) {
        this.mainTicketId = mainTicketId;
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

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "TvmSubTicket{" +
                "id=" + id +
                ", mainTicketId=" + mainTicketId +
                ", ticketLogicNum='" + ticketLogicNum + '\'' +
                ", transDate='" + transDate + '\'' +
                ", transAmount='" + transAmount + '\'' +
                ", createTime='" + createTime + '\'' +
                '}';
    }
}
