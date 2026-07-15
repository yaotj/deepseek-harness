 package com.chinasofti.huateng.collectpay.model.response;

 /**
  * IF8A-09 请求支付响应报文。
  */
 public class RequestPayRespDTO {
     /**
      * 返回码。
      */
     private String retCode;

     /**
      * 返回消息。
      */
     private String retMsg;

     /**
      * 订单号。
      */
     private String orderNo;

     /**
      * 商户订单号。
      */
     private String merchantOrderNo;

     /**
      * 渠道订单号。
      */
     private String channelOrderNo;

     /**
      * 渠道返回数据（支付跳转URL或二维码等）。
      */
     private String data;

     public String getRetCode() {
         return retCode;
     }

     public void setRetCode(String retCode) {
         this.retCode = retCode;
     }

     public String getRetMsg() {
         return retMsg;
     }

     public void setRetMsg(String retMsg) {
         this.retMsg = retMsg;
     }

     public String getOrderNo() {
         return orderNo;
     }

     public void setOrderNo(String orderNo) {
         this.orderNo = orderNo;
     }

     public String getMerchantOrderNo() {
         return merchantOrderNo;
     }

     public void setMerchantOrderNo(String merchantOrderNo) {
         this.merchantOrderNo = merchantOrderNo;
     }

     public String getChannelOrderNo() {
         return channelOrderNo;
     }

     public void setChannelOrderNo(String channelOrderNo) {
         this.channelOrderNo = channelOrderNo;
     }

     public String getData() {
         return data;
     }

     public void setData(String data) {
         this.data = data;
     }

     @Override
     public String toString() {
         return "RequestPayRespDTO{" +
                 "retCode='" + retCode + '\'' +
                 ", retMsg='" + retMsg + '\'' +
                 ", orderNo='" + orderNo + '\'' +
                 ", channelOrderNo='" + channelOrderNo + '\'' +
                 '}';
     }
 }
