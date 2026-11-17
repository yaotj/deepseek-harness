package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;

import java.time.LocalDateTime;


@Data
public class AlipayRefundTxnDetail {

    private Long id;

    private String refundOrderNo;
    private String orderNo;
    private String refundStatus;

    private Integer refundAmount;
    private String refundReason;

    private String merchantRefundNo;
    private String refundNo;
    private String channelRefundNo;

    private Integer requestCount;
    private LocalDateTime nextRequestTime;
    private LocalDateTime lastRequestTime;

    private String refundTime;
    private String txnDate;

    private String operator;
    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
