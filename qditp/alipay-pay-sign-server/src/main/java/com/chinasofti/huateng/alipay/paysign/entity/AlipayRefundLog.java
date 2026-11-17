package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;
import java.time.LocalDateTime;


@Data
public class AlipayRefundLog {

    private String refundSeq;


    private String thirdUserId;


    private String cardId;


    private String orderNo;


    private String refundAmount;


    private String refundStatus;


    private String refundReason;


    private String cardIssueCode;


    private String channelAgreementNo;


    private String refundOrderNo;


    private String requestBody;


    private String responseBody;


    private String resultCode;


    private String resultMsg;


    private String operator;


    private String ip;


    private String remark;


    private String deleteFlag;


    private String version;


    private LocalDateTime createTime;


    private LocalDateTime updateTime;


    private String createBy;
}
