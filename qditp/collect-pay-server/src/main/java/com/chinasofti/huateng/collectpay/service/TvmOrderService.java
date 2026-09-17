package com.chinasofti.huateng.collectpay.service;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestPaymentReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.*;

/** TVM扫码购票业务服务接口。 */
public interface TvmOrderService {

    /**
     * IF2A-01 提交单程票订单。
     *
     * @param request 请求参数（包含公共参数deviceId等）
     * @return 响应结果
     */
    JSONObject requestTvmPayOrder(RequestGenSjtOrderReqDTO request);

    /**
     * IF2A-01 提交单程票订单（仅下单）。
     *
     * @param request 请求参数（包含公共参数deviceId等）
     * @return 响应结果，仅包含订单号
     */
    JSONObject createBomSaleOrder(RequestGenSjtOrderReqDTO request);

    /**
     * IF2A-11 扫码支付。
     *
     * @param request 请求参数（包含订单号、付款码等）
     * @return 响应结果，包含支付结果
     */
    JSONObject requestPayment(RequestPaymentReqDTO request);

    /**
     * IF2A-03 查询支付结果。
     *
     * @param request 请求参数（包含公共参数deviceId等）
     * @return 响应结果
     */
    JSONObject requestPayResult(RequestPayResultReqDTO request);

    /**
     * IF2A-04 出票结果通知。
     *
     * @param request 请求参数（包含公共参数deviceId等）
     * @return 响应结果
     */
    JSONObject notiTakeTicketResult(NotiTakeTicketResultReqDTO request);

    /**
     * IF2A-05 出票故障通知。
     *
     * @param request 请求参数（包含公共参数deviceId等）
     * @return 响应结果
     */
    JSONObject notiTakeTicketFailResult(NotiTakeTicketFailResultReqDTO request);

    /**
     * 退款。
     *
     * @param request 请求参数（包含订单号等）
     * @return 响应结果，包含退款结果
     */
    JSONObject requestRefund(RequestRefundReqDTO request);

    JSONObject requestPayOrderDetail(RequestPayResultReqDTO request);

    // 支付结果通知
    JSONObject payNotice(PayNoticeReqDTO request);

    boolean sendNoticeAppTakeTicketRecord(String payOrderNo, String orderTicketNum, String actualTakeTicketNum, String takeTickeDate,String retryTimes);

    boolean sendNoticeAppTakeTicketFailureRecord(String payOrderNo, String orderTicketNum, String actualTakeTicketNum, String takeTickeDate,String takeTiketFaultReason,String refundAmount, String retryTimes);
}
