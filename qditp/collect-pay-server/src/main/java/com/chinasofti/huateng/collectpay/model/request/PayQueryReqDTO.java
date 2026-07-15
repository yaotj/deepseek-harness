 package com.chinasofti.huateng.collectpay.model.request;

 /**
  * IF8A-10 支付查询请求报文。
  */
 public class PayQueryReqDTO {
     /**
      * 订单号。
      */
     private String orderNo;

     /**
      * 商户订单号。
      */
     private String merchantOrderNo;

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

     @Override
     public String toString() {
         return "PayQueryReqDTO{" +
                 "orderNo='" + orderNo + '\'' +
                 ", merchantOrderNo='" + merchantOrderNo + '\'' +
                 '}';
     }
 }
