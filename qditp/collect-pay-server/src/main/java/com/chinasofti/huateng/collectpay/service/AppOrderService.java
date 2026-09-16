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
     * <p>与 {@link #requestRefundTicket} 的唯一区别是金额来源：那条取
     * {@code TBL_TVM_APP_ORDER.PAY_AMOUNT} 全额，因此对已退过一部分的订单必然超额、被支付
     * 中心拒（2026-09-14 实测订单 {@code 00202609111609085124} 付 600 已退 200，走旧接口会
     * 按 600 提交）。**旧接口按原样保留、NEVER 改它的金额来源** —— 它是 APP 对外契约的一部分，
     * APP 端只传 {@code orderNo}，加字段等于改契约。</p>
     *
     * <p>本方法自带**超退闸门**：可退余额 = {@code PAY_AMOUNT} -
     * {@link com.chinasofti.huateng.collectpay.mapper.AppRefundOrderMapper#sumSuccessRefundAmount}，
     * 入参超过余额直接拒、**不发起支付中心调用、不落退款单**。这一层是本方法存在的主要价值，
     * **NEVER 为了「让运营能强退」把它去掉**。</p>
     *
     * <p>⚠️ 不做幂等：与本模块其它退款入口一致，同一订单连调两次会退两次（退款操作手册铁律 2）。
     * 调用方 MUST 自己控制只调一次，中断后 MUST 先查 {@code TBL_APP_ORDER_REFUND} 再续跑。</p>
     *
     * @param payOrderNo   原支付订单号
     * @param refundAmount 本次退款金额（分，正整数）
     * @return 与旧接口同形的应答；被闸门拒绝时 {@code retCode != 0000} 且不产生退款单
     */
    JSONObject refundByAmount(String payOrderNo, int refundAmount);


    public boolean noticeAppRefundResult(String payOrderNo, String refundResult, String refundDate, String refundAmount, String retryTimes);

}