package com.chinasofti.huateng.collectpay.entity;


import lombok.Data;

@Data
public class NoticeTakeTicketFailureRecord {

    private String orderNo;
    private String orderTicketNum;
    private String actualTakeTicketNum;
    private String takeTickeDate;
    private String takeTiketFaultReason;
    private String refundAmount;
    private String status;
    private String retryTimes;
    private String createTime;
    private String updateTime;
}
