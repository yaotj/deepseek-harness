package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.paysign.ResendSignNotifyRespDTO;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;

public interface AppNotifyService {
    /**
     * 异步通知App签约结果。
     *
     * @param request 流水记录
     * @param signInfo 签约信息
     * @param receiveRequest 支付平台回调的签约结果请求
     */
    void asyncNotifySignResult(PaySignRequest request, PaySignInfo signInfo, ReceiveSignResultReqDTO receiveRequest);

    /** 异步通知 APP 解约成功。 */
    void asyncNotifyTerminationResult(AppTerminationRequest terminationRequest, PaySignInfo signInfo, ReceiveTerminationResultReqDTO receiveRequest);

    /** 异步通知 APP 解约失败（存在扣费失败订单或支付平台解约失败） */
    void asyncNotifyTerminationFailed(AppTerminationRequest terminationRequest, NotifyTerminationFailedReqDTO receiveRequest);

    /** 重发一条解约结果通知（解约通知补偿任务逐条调用）。 */
    void asyncRetryTerminationNotify(AppTerminationRequest terminationRequest);

    /** 签约流水通知补偿：扫一批 APP_PAY_SIGN_REQUEST 里 NOTIFY_STATUS=FAILED 且未超重试上限的记录重发通知。 */
    CompensateNotifyRespDTO compensateSignNotify();

    /** 重发指定流水号的签约结果通知（内部接口 /internal/paySign/resendNotify）。 */
    ResendSignNotifyRespDTO resendSignNotify(String requestSignSeq);
}
