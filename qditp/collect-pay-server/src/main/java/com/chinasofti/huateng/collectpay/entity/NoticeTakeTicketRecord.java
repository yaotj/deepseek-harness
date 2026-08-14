package com.chinasofti.huateng.collectpay.entity;


import lombok.Data;

@Data
public class NoticeTakeTicketRecord {

    private String orderNo;
    private String orderTicketNum;
    private String actualTakeTicketNum;
    private String takeTickeDate;
    private String status;
    private String retryTimes;
    private String createTime;
    private String updateTime;
}
