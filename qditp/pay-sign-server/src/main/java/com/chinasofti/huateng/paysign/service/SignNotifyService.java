package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.ResendSignNotifyRespDTO;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;

/**
 * IF8B 签约结果通知（**只管签约聚合**）。
 *
 * <p>2026-09-17（ADR-D127）由 {@code AppNotifyService} 按聚合拆出：原接口同时管签约与解约两条聚合、
 * 实现类因此注了 3 个 mapper（`APP_PAY_SIGN_REQUEST` / `APP_PAY_SIGN_INFO` / `APP_TERMINATION_REQUEST`），
 * 6 个注入方里有 4 个只用解约那 3 个方法。解约侧现为 {@link TerminationNotifyService}，
 * **NEVER 把两者合回一个接口**。
 *
 * <p>方法参数**刻意保留 entity**：这些方法真实读到 11 / 5 个字段，摊成标量参数只会更糟；
 * 而「只传主键、实现内回查」会破坏 {@code TerminationNotifyService} 那侧的轮次闸门语义
 * （详见该接口注释），两侧口径统一按「调用方给快照」，NEVER 改成回查。
 */
public interface SignNotifyService {
    /**
     * 异步通知App签约结果。
     *
     * @param request 流水记录
     * @param signInfo 签约信息
     * @param receiveRequest 支付平台回调的签约结果请求
     */
    void asyncNotifySignResult(PaySignRequest request, PaySignInfo signInfo, ReceiveSignResultReqDTO receiveRequest);

    /** 签约流水通知补偿：扫一批 APP_PAY_SIGN_REQUEST 里 NOTIFY_STATUS=FAILED 且未超重试上限的记录重发通知。 */
    CompensateNotifyRespDTO compensateSignNotify();

    /** 重发指定流水号的签约结果通知（内部接口 /internal/paySign/resendNotify）。 */
    ResendSignNotifyRespDTO resendSignNotify(String requestSignSeq);
}
