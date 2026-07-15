package com.chinasofti.huateng.collectpay.service;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestActiveTicketReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestTakeTicketAuthReqDTO;

/**
 * TVM扫码取票服务接口。
 */
public interface TvmTakeTicketService {

    /**
     * IF8A-15 激活取票订单。
     * 用户扫码TVM二维码后，手机调用此接口激活取票订单。
     *
     * @param request 请求参数
     * @return 响应结果
     */
    JSONObject requestActiveTicket(RequestActiveTicketReqDTO request);

    /**
     * IF2A-08 扫码取票订单查询。
     * TVM轮询查询激活状态。
     *
     * @param request 请求参数
     * @return 响应结果
     */
    JSONObject requestTakeTicketAuth(RequestTakeTicketAuthReqDTO request);
}
