package com.chinasofti.huateng.collectpay.service;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.model.request.tvm.NotiTakeTicketFailResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.NotiTakeTicketResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestGenSjtOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.NotiTakeTicketFailResultRespDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.NotiTakeTicketResultRespDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.TvmOrderResult;
import com.chinasofti.huateng.collectpay.model.response.tvm.RequestPayResultRespDTO;

/**
 * TVM扫码购票业务服务接口。
 */
public interface TvmOrderService {

    /**
     * IF2A-01 提交单程票订单。
     * TVM向ITP平台发起提交单程票订单请求，ITP返回支付URL。
     *
     * @param request 请求参数（包含公共参数deviceId等）
     * @return 响应结果
     */
    JSONObject requestTvmPayOrder(RequestGenSjtOrderReqDTO request);

    /**
     * IF2A-03 查询支付结果。
     * TVM轮询查询支付结果，ITP返回支付状态。
     *
     * @param request 请求参数（包含公共参数deviceId等）
     * @return 响应结果
     */
    JSONObject requestPayResult(RequestPayResultReqDTO request);

    /**
     * IF2A-04 出票结果通知。
     * TVM出票成功后通知ITP平台。
     *
     * @param request 请求参数（包含公共参数deviceId等）
     * @return 响应结果
     */
    JSONObject notiTakeTicketResult(NotiTakeTicketResultReqDTO request);

    /**
     * IF2A-05 出票故障通知。
     * TVM出票故障时通知ITP平台。
     *
     * @param request 请求参数（包含公共参数deviceId等）
     * @return 响应结果
     */
    JSONObject notiTakeTicketFailResult(NotiTakeTicketFailResultReqDTO request);
}
