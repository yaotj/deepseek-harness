package com.chinasofti.huateng.collectpay.entity;

import lombok.Data;

/**
 * 出票明细记录表实体（tbl_tvm_sub_ticket/tbl_bom_sub_ticket）。
 */
@Data
public class SubTicket {
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
    private String businessType;

}
