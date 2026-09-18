package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.model.app.AppSignResultNotifyReqDTO;
import com.chinasofti.huateng.model.app.AppTerminationResultNotifyReqDTO;

/**
 * APP 出向通知（IF8B 族）的出网端口。
 *
 * <p>调用方只交出 bizData，**不知道** 目标地址、ITP 报文骨架、时间戳格式与加签方式 ——
 * 这四样全部只在 {@link AppNotifyHttpAdapter} 里出现一次。
 *
 * <p>NEVER 在这里加「重试」「补偿」「落库」语义：本端口只负责推一次并如实回答结果。
 */
public interface AppNotifyPort {

    /** 推送签约结果通知（{@code app.notify.sign-result-url}）。 */
    NotifyDelivery pushSignResult(AppSignResultNotifyReqDTO bizData);

    /** 推送解约结果通知，成功与失败共用同一个地址（{@code app.notify.termination-result-url}）。 */
    NotifyDelivery pushTerminationResult(AppTerminationResultNotifyReqDTO bizData);
}
