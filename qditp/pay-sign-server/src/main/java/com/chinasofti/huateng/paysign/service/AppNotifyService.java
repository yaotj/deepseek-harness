package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;

public interface AppNotifyService {
    /**
     * 异步通知App签约结果
     * @param request 流水记录
     * @param signInfo 签约信息
     * @param receiveRequest 支付平台回调的签约结果请求
     */
    void asyncNotifySignResult(PaySignRequest request, PaySignInfo signInfo, ReceiveSignResultReqDTO receiveRequest);

    /**
     * 异步通知App解约结果
     * @param request 流水记录
     * @param signInfo 签约信息
     * @param receiveRequest 支付平台回调的解约结果请求
     */
    void asyncNotifyTerminationResult(PaySignRequest request, PaySignInfo signInfo, ReceiveTerminationResultReqDTO receiveRequest);

    /**
     * 补偿通知（定时任务调用）
     * 重试失败的通知
     */
    void compensateNotify();
}
