package com.chinasofti.huateng.collectpay.entity;


import lombok.Data;

@Data
public class NoticeRefundRecord {

    private String orderNo;
    private String refundType;
    private String refundResult;
    private String refundResultDesc;
    private String refundDate;
    private String refundAmount;
    private String status;
    private String retryTimes;
    private String createTime;
    private String updateTime;
}
