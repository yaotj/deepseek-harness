package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;

import java.time.LocalDateTime;


@Data
public class AlipayPayCenterMsgLog {

    private Long id;

    private String orderNo;
    private String txnDate;
    private String apiName;
    private String requestNo;

    private String scene;
    private String industryType;
    private String ipAddress;

    private String retCode;
    private String retMsg;
    private Long elapsedMs;

    private String requestBody;
    private String responseBody;
    private String remark;

    private LocalDateTime createTime;
}
