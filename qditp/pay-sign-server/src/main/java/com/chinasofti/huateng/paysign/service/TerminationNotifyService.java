package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;

/**
 * IF8B 解约结果通知（**只管解约聚合**）。
 *
 * <p>2026-09-17（ADR-D127）由 {@code AppNotifyService} 按聚合拆出，签约侧见 {@link SignNotifyService}。
 *
 * <p><b>参数是 entity 而非主键，这是刻意的，NEVER 改成「只传 requestSignSeq、实现内回查」</b>：
 * 通知结果回写带轮次闸门（ADR-D125），闸门比对的是**本次通知所描述的那一轮终态**。
 * 异步投递可能比 {@code requestTermination} 里的 {@code reactivateFailed} 晚返回，
 * 那时库里已是新一轮 PENDING —— 回查拿到的是新一轮的状态，闸门就废了。
 */
public interface TerminationNotifyService {
    /** 异步通知 APP 解约成功。 */
    void asyncNotifyTerminationResult(AppTerminationRequest terminationRequest, PaySignInfo signInfo,
                                      ReceiveTerminationResultReqDTO receiveRequest);

    /** 异步通知 APP 解约失败（存在扣费失败订单或支付平台解约失败） */
    void asyncNotifyTerminationFailed(AppTerminationRequest terminationRequest,
                                      NotifyTerminationFailedReqDTO receiveRequest);

    /** 重发一条解约结果通知（解约通知补偿任务逐条调用）。 */
    void asyncRetryTerminationNotify(AppTerminationRequest terminationRequest);
}
