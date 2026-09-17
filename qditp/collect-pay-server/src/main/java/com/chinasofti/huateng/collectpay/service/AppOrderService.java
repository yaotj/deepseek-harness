package com.chinasofti.huateng.collectpay.service;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.model.request.app.RequestOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.app.RequestPayInfoReqDTO;
import com.chinasofti.huateng.collectpay.model.request.app.RequestQueryActiveOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.APPRefundNotiResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.PayNoticeReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestPayResultReqDTO;

/** APP订单服务接口。 */
public interface AppOrderService {

    /**
     * IF8A-20 请求下单。
     *
     * @param request 请求参数
     * @return 应答结果，包含订单号
     */
    JSONObject requestOrder(RequestOrderReqDTO request);

    /**
     * IF8A-11 请求支付信息。
     *
     * @param request 请求参数
     * @return 应答结果，包含支付通道编码、支付信息、签名类型和签名
     */
    JSONObject requestPayInfo(RequestPayInfoReqDTO request);

    /**
     * IF8A-18 支付结果查询。
     *
     * @param request 请求参数
     * @return 应答结果，包含交易流水号、支付结果、支付金额、支付时间
     */
    JSONObject requestPayResult(RequestPayResultReqDTO request);

    JSONObject requestRefundTicket(RequestPayResultReqDTO request);
    JSONObject refundAppNotTakeTickets();

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

    public JSONObject doRefund(String payOrderNo, String refundAmount,String refundNo, String businessType);

    /**
     * 【新增，2026-09-14】按**指定金额**给 APP 取票订单退款，用于补退「已部分退款的剩余部分」。
     *
     * @param payOrderNo   原支付订单号
     * @param refundAmount 本次退款金额（分，正整数）
     * @return 与旧接口同形的应答；被闸门拒绝时 {@code retCode != 0000} 且不产生退款单
     */
    JSONObject refundByAmount(String payOrderNo, int refundAmount);


    public boolean noticeAppRefundResult(String payOrderNo, String refundResult, String refundDate, String refundAmount, String retryTimes);

}