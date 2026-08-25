package com.chinasofti.huateng.collectpay.service;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.model.request.bom.*;
import com.chinasofti.huateng.collectpay.model.request.tvm.NotiTakeTicketFailResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.NotiTakeTicketResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.PayNoticeReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestGenSjtOrderReqDTO;

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
     * bom发售订单
     */
    JSONObject requestBomSaleOrder(RequestGenSjtOrderReqDTO request);

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
     * @return 响应结果，包含支付结果（SUCCESS/FAILED/PROCESSING）
     */
//    JSONObject requestGetPayResult(RequestGetPayResultReqDTO request);
    JSONObject requestGetPayResult(String payOrderNo);

    /**
     * IF2A-08 业务操作结果通知。
     * BOM业务操作完成后，向ITP平台通知操作结果。
     *
     * @param request 请求参数（包含订单号、操作结果等）
     * @return 响应结果
     */
    JSONObject notiBusResult(NotiBusResultReqDTO request,String transType);



    JSONObject notiBomSaleResult(NotiTakeTicketFailResultReqDTO request);

    // 支付结果通知
    JSONObject payNotice(PayNoticeReqDTO request);

    /**
     * 充值结果通知。
     * BOM充值操作完成后，向ITP平台通知充值结果。
     *
     * @param request 请求参数（包含订单号、充值状态等）
     * @return 响应结果
     */
    JSONObject notiTopupResult(NotiTopupResultReqDTO request);

    /**
     * IF5A-01 请求票卡分析。
     * 后付费二维码票分析。
     *
     * @param request 请求参数（包含发行方代码、手机号、逻辑卡号、更新区域类型等）
     * @return 响应结果，包含票卡分析结果
     */
    JSONObject requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request);

    /**
     * IF5A-03 请求票卡更新。
     *
     * @param request 请求参数（包含逻辑卡号、更新区域类型、建议操作类型、操作员编码、补站站点、更新时间、交易金额等）
     * @return 响应结果，包含最新行业数据
     */
    JSONObject requestCardDataUpdate(RequestCardDataUpdateReqDTO request);

    JSONObject requestOrderTResult(RequestOrderResultReqDTO request);
    JSONObject requestTicketTRefund(RequestTicketRefundReqDTO request);

    /**
     * IF2A-04 出票结果通知。
     * TVM出票成功后通知ITP平台。
     *
     * @param request 请求参数（包含公共参数deviceId等）
     * @return 响应结果
     */
    JSONObject notiTakeTicketResult(NotiTakeTicketResultReqDTO request);

    JSONObject notiTakeTicketFailResult(NotiTakeTicketFailResultReqDTO request);

    /**
     * IF5A-09 HCE票卡更新结果通知。
     * BOM更新HCE票数据后，向ITP平台通知更新结果。
     *
     * @param request 请求参数（包含卡号、HCE数据、操作类型等）
     * @return 响应结果
     */
    JSONObject notiUpdateHceData(NotiUpdateHceDataReqDTO request);
}