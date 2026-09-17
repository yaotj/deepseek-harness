package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.paysign.model.request.CheckFailedOrdersReqDTO;
import com.chinasofti.huateng.paysign.model.request.ExecuteTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.CheckFailedOrdersRespDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationReqDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementReqDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;

/** ITP 解约内部接口服务。 */
public interface TerminationInternalService {

    /**
     * 批量处理待解约申请：扫一批 PENDING + SCANNING 记录，PENDING 逐条先查未结清扣费订单。
     *
     * @param request 可为 null。null 或两个字段都为空时**不按申请时间过滤**，行为与历史一致；
     */
    ProcessTerminationRespDTO processTermination(ProcessTerminationReqDTO request);

    /** 解约通知补偿：扫一批 APP_TERMINATION_REQUEST 中 NOTIFY_STATUS=FAILED 且未超重试上限的记录重发通知。 */
    CompensateNotifyRespDTO compensateTerminationNotify();

    /** 通道清理补偿：重推「解约已收口成 SUCCESS、但账户域支付通道还没删掉」的记录（ADR-D48）。 */
    CompensateNotifyRespDTO compensateChannelSync();

    /** 查询用户是否存在扣费失败订单。 */
    CheckFailedOrdersRespDTO checkFailedOrders(CheckFailedOrdersReqDTO request);

    /** 执行支付平台解约。 */
    BaseRespDTO executeTermination(ExecuteTerminationReqDTO request);

    /**
     * IF8A-75 直接解绑支付方式：APP 侧入口，立即向支付渠道发起解绑，不等账期结束的定时任务。
     *
     * @return {@code 0000} 表示<b>已向支付渠道发起</b>，不代表已解绑完成，收口以
     */
    UnbindAgreementResult unbindAgreement(UnbindAgreementReqDTO request);

    /** 通知 APP 解约失败。 */
    BaseRespDTO notifyTerminationFailed(NotifyTerminationFailedReqDTO request);
}
