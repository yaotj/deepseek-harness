package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 支付宝解约登记表实体。
 */
@Data
public class AlipayTerminationRequest {
    private String terminationSeq;
    private String agreementCode;
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String channel;
    private String merchantNo;
    private String operationType;
    private String status;
    private String terminationResult;
    private LocalDateTime terminationTime;
    private String deleteFlag;
    private String version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
