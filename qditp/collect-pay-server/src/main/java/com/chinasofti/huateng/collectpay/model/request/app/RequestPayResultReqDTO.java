//package com.chinasofti.huateng.collectpay.model.request.app;
//
//
//import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
//import lombok.Data;
//
///**
// * IF8A-18 支付结果查询请求参数DTO。
// * APP_SERVER向ITP平台发起支付结果查询的参数封装。
// */
//@Data
//public class RequestPayResultReqDTO extends BaseRequestDTO {
//
//    /**
//     * 用户编码。
//     */
//    private String userId;
//
//    /**
//     * 订单号。
//     */
//    private String orderNo;
//
//    public String getUserId() {
//        return userId;
//    }
//
//    public void setUserId(String userId) {
//        this.userId = userId;
//    }
//
//    public String getOrderNo() {
//        return orderNo;
//    }
//
//    public void setOrderNo(String orderNo) {
//        this.orderNo = orderNo;
//    }
//
//    @Override
//    public String toString() {
//        return "RequestPayResultReqDTO{" +
//                "userId='" + userId + '\'' +
//                ", orderNo='" + orderNo + '\'' +
//                '}';
//    }
//}