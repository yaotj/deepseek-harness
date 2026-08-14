package com.chinasofti.huateng.collectpay.entity;

import lombok.Data;

/**
 * 取票故障通知记录实体（TBL_NOTICE_APP_FAILURE_RECORD）。
 */
@Data
public class NoticeTakeTicketFailureRecord {

    /** 订单号 */
    private String orderNo;

    /** 订购票数 */
    private String orderTicketNum;

    /** 实际取票数 */
    private String actualTakeTicketNum;

    /** 取票日期（格式 yyyyMMdd） */
    private String takeTickeDate;

    /** 取票故障原因 */
    private String takeTiketFaultReason;

    /** 退款金额（单位：分） */
    private String refundAmount;

    /** 通知状态 */
    private String status;

    /** 重试次数 */
    private String retryTimes;

    /** 创建时间 */
    private String createTime;

    /** 更新时间 */
    private String updateTime;
}
