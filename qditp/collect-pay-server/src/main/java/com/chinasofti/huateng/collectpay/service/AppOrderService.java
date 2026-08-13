package com.chinasofti.huateng.collectpay.service;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.model.request.app.RequestOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.app.RequestPayInfoReqDTO;
import com.chinasofti.huateng.collectpay.model.request.app.RequestQueryActiveOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.APPRefundNotiResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.PayNoticeReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestPayResultReqDTO;

/**
 * APP订单服务接口。
 * 定义APP下单和支付相关的业务逻辑方法。
 */
public interface AppOrderService {

    /**
     * IF8A-20 请求下单。
     * APP_SERVER向ITP平台发起下单请求。
     *
     * @param request 请求参数
     * @return 应答结果，包含订单号
     */
    JSONObject requestOrder(RequestOrderReqDTO request);

    /**
     * IF8A-11 请求支付信息。
     * APP_SERVER向ITP平台发起支付请求，ITP根据支付通道编码创建支付订单，
     * 请求对应的支付通道预下单，将预下单返回的支付信息签名后返回。
     *
     * @param request 请求参数
     * @return 应答结果，包含支付通道编码、支付信息、签名类型和签名
     */
    JSONObject requestPayInfo(RequestPayInfoReqDTO request);

    /**
     * IF8A-18 支付结果查询。
     * APP_SERVER向ITP平台发起支付结果查询。
     * 先查数据库，如果数据库有成功或者失败的结果，则直接返回；
     * 如果没有成功或者失败的结果，则请求tvmOrderPreService.requestPayResult()方法查询支付结果。
     *
     * @param request 请求参数
     * @return 应答结果，包含交易流水号、支付结果、支付金额、支付时间
     */
    JSONObject requestPayResult(RequestPayResultReqDTO request);

    JSONObject requestRefundTicket(RequestPayResultReqDTO request);

    JSONObject requestRefundTicketResult(RequestPayResultReqDTO request);

    JSONObject requestPreActiveOrderList(RequestQueryActiveOrderReqDTO request);

    JSONObject receiveRefundResult(APPRefundNotiResultReqDTO request);

//    /**
//     * IF8B-05 支付结果通知。
//     * 第三方支付通道通知ITP平台支付结果，ITP更新订单状态并通知APP。
//     *
//     * @param request 请求参数
//     * @return 应答结果
//     */
//    JSONObject receivePaymentResult(JSONObject request);

    // 支付结果通知
    JSONObject payNotice(PayNoticeReqDTO request);
}