package com.chinasofti.huateng.collectpay.service;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.model.request.tvm.*;

/** TVM扫码充值服务接口。 */
public interface TvmTopupService {

    /**
     * IF2A-09 请求充值下单。
     *
     * @param request 请求参数
     * @return 响应结果
     */
    JSONObject requestTopup(RequestTopupReqDTO request);

    JSONObject requestPayResult(RequestPayResultReqDTO request);
    JSONObject refundTvmTopupNotTakeTickets();

    /**
     * IF2A-06 充值结果通知。
     *
     * @param request 请求参数
     * @return 响应结果
     */
    JSONObject topupCardResultNoti(TopupCardResultNotiReqDTO request);

    /**
     * IF2A-07 充值失败通知。
     *
     * @param request 请求参数
     * @return 响应结果
     */
    JSONObject topupCardFailNoti(TopupCardFailNotiReqDTO request);

    JSONObject requestPayOrderDetail(RequestPayResultReqDTO request);

    JSONObject requestRefund(RequestRefundReqDTO request);

    // 支付结果通知
    JSONObject payNotice(PayNoticeReqDTO request);
}
