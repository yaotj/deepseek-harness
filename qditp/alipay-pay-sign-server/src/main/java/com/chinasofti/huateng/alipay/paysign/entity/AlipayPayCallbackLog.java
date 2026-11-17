package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;

import java.time.LocalDateTime;


@Data
public class AlipayPayCallbackLog {

    private String callbackSeq;
    private String orderNo;

    private String callbackType;

    private String transStatus;
    private String channelVoucherId;
    private String transAmount;
    private String transTime;

    private Integer totalAmount;
    private String txnDate;


    private String refundOrderNo;
    private Integer refundAmount;
    private String refundStatus;

    private String rawBody;
    private String handleStatus;
    private String handleMsg;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
