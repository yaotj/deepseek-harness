package com.chinasofti.huateng.collectpay.service;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.model.request.bom.NotiBusResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestGenNoCashOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestGetPayResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestPaymentReqDTO;

/**
 * BOM非现金业务服务接口。
 * 提供BOM非现金收款业务的核心业务逻辑。
 */
public interface BomOrderService {

    /**
     * IF8A-04 请求非现金收款下单。
     * BOM向ITP平台发起非现金收款订单请求，ITP生成订单并返回订单号。
     *
     * @param request 请求参数（包含公共参数deviceId等）
     * @return 响应结果，包含订单号
     */
    JSONObject requestGenNoCashOrder(RequestGenNoCashOrderReqDTO request);

    /**
     * IF8A-05 扫码支付。
     * BOM扫描用户付款码后，向ITP平台发起支付请求，ITP调用支付中心完成支付。
     *
     * @param request 请求参数（包含订单号、付款码等）
     * @return 响应结果，包含支付结果
     */
    JSONObject requestPayment(RequestPaymentReqDTO request);

    /**
     * IF8A-06 查询支付结果。
     * BOM轮询查询支付结果，ITP调用支付中心查询并返回支付状态。
     *
     * @param request 请求参数（包含订单号）
     * @return 响应结果，包含支付结果（SUCCESS/FAILED/PROCESSING）
     */
    JSONObject requestGetPayResult(RequestGetPayResultReqDTO request);

    /**
     * IF2A-08 业务操作结果通知。
     * BOM业务操作完成后，向ITP平台通知操作结果。
     *
     * @param request 请求参数（包含订单号、操作结果等）
     * @return 响应结果
     */
    JSONObject notiBusResult(NotiBusResultReqDTO request);
}