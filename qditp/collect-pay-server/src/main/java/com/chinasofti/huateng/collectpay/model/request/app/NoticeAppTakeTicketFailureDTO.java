package com.chinasofti.huateng.collectpay.model.request.app;

import lombok.Data;

/**
 * 5.2 取票故障通知 DTO。
 * TVM 扫码取票出现故障时，向 ITP 平台/App 推送取票失败结果。
 */
@Data
public class NoticeAppTakeTicketFailureDTO {

    /** 支付订单号 */
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
}
