package com.chinasofti.huateng.alipay.paysign.entity;

import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import lombok.Data;


@Data
public class AlipayPayLog {

    private String paySeq;


    private String thirdUserId;


    private String cardId;


    private String orderNo;


    private String tradeNo;


    private String payAmount;


    private String payStatus;


    private String payType;


    private String scene;


    private String paymentVendor;


    private String industryType;


    private String subject;


    private String body;


    private String requestSignSeq;


    private String orderTimeOut;


    private String authCode;


    private String notifyUrl;


    private String returnUrl;


    private String ipAddress;


    private String industryDetail;


    private String requestBody;


    private String responseBody;


    private String resultCode;


    private String resultMsg;


    private String remark;


    private String refundAmount;


    private String refundStatus;


    private String entryId;


    private String exitId;


    private String invoice;


    private String transTime;
}
