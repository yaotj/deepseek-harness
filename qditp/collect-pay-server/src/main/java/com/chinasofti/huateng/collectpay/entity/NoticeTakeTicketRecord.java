package com.chinasofti.huateng.collectpay.entity;

import lombok.Data;

/**
 * 取票通知记录实体（tbl_notice_app_taketicket_record）。
 */
@Data
public class NoticeTakeTicketRecord {

    /** 订单号 */
    private String orderNo;

    /** 订购票数 */
    private Integer orderTicketNum;

    /** 实际取票数 */
    private Integer actualTakeTicketNum;

    /** 取票日期（格式 yyyyMMdd） */
    private String takeTickeDate;

    /** 通知状态 */
    private String status;

    /** 重试次数 */
    private Integer retryTimes;

    /** 创建时间 */
    private String createTime;

    /** 更新时间 */
    private String updateTime;
}
