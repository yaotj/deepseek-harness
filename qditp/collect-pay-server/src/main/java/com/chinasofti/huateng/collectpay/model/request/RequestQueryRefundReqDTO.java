 package com.chinasofti.huateng.collectpay.model.request;

 import lombok.Data;

 /**
  * 请求查询退款请求报文。
  */
 @Data
 public class RequestQueryRefundReqDTO {
     /**
      * 退款订单号。
      */
//     private String refundOrderNo;
     private String merchantRefundNo;

 }
