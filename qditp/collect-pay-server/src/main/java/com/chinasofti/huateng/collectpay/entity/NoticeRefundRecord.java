package com.chinasofti.huateng.collectpay.entity;

import lombok.Data;

/**
 * 退款通知记录实体（tbl_notice_app_refund_record）。
 */
@Data
public class NoticeRefundRecord {

    /** 订单号 */
    private String orderNo;

    /** 退款类型 */
    private Integer refundType;

    /** 退款结果（1=成功 2=失败） */
    private Integer refundResult;

    /** 退款结果描述 */
    private String refundResultDesc;

    /** 退款日期 */
    private String refundDate;

    /** 退款金额（单位：分） */
    private Integer refundAmount;

    /** 通知状态 */
    private Integer status;

    /** 重试次数 */
    private Integer retryTimes;

    /** 创建时间 */
    private String createTime;

    /** 更新时间 */
    private String updateTime;
}
