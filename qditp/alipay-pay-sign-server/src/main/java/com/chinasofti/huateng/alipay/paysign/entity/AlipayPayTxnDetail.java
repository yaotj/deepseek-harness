package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;

import java.time.LocalDateTime;


@Data
public class AlipayPayTxnDetail {

    private Long id;

    private String orderNo;
    private String txnDate;


    private String payStatus;

    private Integer amount;

    private String refundStatus;
    private Integer refundAmount;
    private LocalDateTime lastRefundTime;

    private String payCenterOrderNo;
    private String channelOrderNo;


    private String transTime;

    private String requestSignSeq;
    private String channelAgreementNo;

    private Integer requestCount;
    private LocalDateTime lastRequestTime;
    private LocalDateTime nextRequestTime;

    private String invoice;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
