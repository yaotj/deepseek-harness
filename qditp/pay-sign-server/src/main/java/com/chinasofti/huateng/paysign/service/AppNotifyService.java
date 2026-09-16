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
     * 异步通知App签约结果
     * @param request 流水记录
     * @param signInfo 签约信息
     * @param receiveRequest 支付平台回调的签约结果请求
     */
    void asyncNotifySignResult(PaySignRequest request, PaySignInfo signInfo, ReceiveSignResultReqDTO receiveRequest);

    /**
     * 异步通知 APP 解约成功
     */
    void asyncNotifyTerminationResult(AppTerminationRequest terminationRequest, PaySignInfo signInfo, ReceiveTerminationResultReqDTO receiveRequest);

    /**
     * 异步通知 APP 解约失败（存在扣费失败订单或支付平台解约失败）
     */
    void asyncNotifyTerminationFailed(AppTerminationRequest terminationRequest, NotifyTerminationFailedReqDTO receiveRequest);

    /**
     * 重发一条解约结果通知（解约通知补偿任务逐条调用）。
     *
     * <p>按 APP_TERMINATION_REQUEST 的终态决定重发成功通知还是失败通知，通知结果仍落回该表。
     * {@link #compensateSignNotify()} 只扫 APP_PAY_SIGN_REQUEST，覆盖不到解约表，两者不可互相替代。</p>
     */
    void asyncRetryTerminationNotify(AppTerminationRequest terminationRequest);

    /**
     * 签约流水通知补偿：扫一批 APP_PAY_SIGN_REQUEST 里 NOTIFY_STATUS=FAILED 且未超重试上限的记录重发通知。
     *
     * <p>本方法**不再由服务内 {@code @Scheduled} 驱动**，改由外部定时任务调用
     * {@code POST /internal/paySign/compensateNotify}，调用方可反复调用直到 scanned 为 0。
     * 重发是异步的，通知是否成功以 APP_PAY_SIGN_REQUEST 的 NOTIFY_STATUS / NOTIFY_RESULT 为准，
     * 不要用返回的 submitted 判断结果。</p>
     */
    CompensateNotifyRespDTO compensateSignNotify();

    /**
     * 重发指定流水号的签约结果通知（内部接口 /internal/paySign/resendNotify）。
     *
     * <p>与 {@link #compensateSignNotify()} 的分工：补偿是**批量扫表**、按 NOTIFY_STATUS 与重试次数
     * 决定捞哪些；本方法只处理指定的一条，**不扫表、不递增 NOTIFY_RETRY_COUNT**，供联调与运维人工重放。
     * NEVER 为了重发一条而去打批量补偿——库里符合扫描条件的历史流水会被一起推给 APP，
     * 而 APP 侧实测没有按 requestSignSeq 幂等，污染的是对端状态、我方无法回滚。</p>
     *
     * <p>前置校验是**白名单**：APP_PAY_SIGN_INFO 必须存在且 SIGN_STATUS='SIGNED'，
     * APP_PAY_SIGN_REQUEST 里必须已有 OPERATION_TYPE='RECEIVE_SIGN_RESULT' 的流水。
     * 缺任何一条都拒绝——没有「签约已成功」这个事实，就不该有签约成功通知发出去。</p>
     *
     * <p>本方法**同步**投递并把结果回写 NOTIFY_STATUS / NOTIFY_TIME / NOTIFY_RESULT，
     * 返回值的 notified 即这次投递的真实结果。</p>
     */
    ResendSignNotifyRespDTO resendSignNotify(String requestSignSeq);
}
