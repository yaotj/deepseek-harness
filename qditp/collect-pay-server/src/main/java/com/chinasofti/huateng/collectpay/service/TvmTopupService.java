package com.chinasofti.huateng.collectpay.service;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestTopupReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.TopupCardFailNotiReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.TopupCardResultNotiReqDTO;

/**
 * TVM扫码充值服务接口。
 */
public interface TvmTopupService {

    /**
     * IF2A-09 请求充值下单。
     * TVM向ITP平台发起充值下单请求，ITP返回支付URL。
     *
     * @param request 请求参数
     * @return 响应结果
     */
    JSONObject requestTopup(RequestTopupReqDTO request);

    JSONObject requestPayResult(RequestPayResultReqDTO request);

    /**
     * IF2A-06 充值结果通知。
     * TVM充值成功后通知ITP平台。
     *
     * @param request 请求参数
     * @return 响应结果
     */
    JSONObject topupCardResultNoti(TopupCardResultNotiReqDTO request);

    /**
     * IF2A-07 充值失败通知。
     * TVM充值失败后通知ITP平台。
     *
     * @param request 请求参数
     * @return 响应结果
     */
    JSONObject topupCardFailNoti(TopupCardFailNotiReqDTO request);
}
