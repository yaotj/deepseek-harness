package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;

/**
 * 支付宝渠道**退款申请聚合**的唯一入口（2026-09-18 迁移第 9 条，接在
 * {@link AlipayPayRequestService} 之后）。
 *
 * <p>只承载「发起一笔退款」这件事：入参校验、查原支付、幂等短路、落
 * {@code ALIPAY_REFUND_LOG}、出网、按支付中心应答回写明细与汇总。
 * <b>退款结果回调不在本接口内</b> —— 那条现在只落回调凭据、不回写业务
 * （{@code service.AlipayPayCallbackService}），与本接口是两个写入方，
 * 合进来就说不清「这笔退款的状态是谁推进的」。
 *
 * <p><b>本条与扣费申请那条的实质差异：退款方向落的是旧表</b>。
 * 扣费申请早已迁到新表 {@code ALIPAY_PAY_TXN_DETAIL}，而<b>退款方向从来没有落新表的第二套实现</b>：
 * 明细在 {@code ALIPAY_REFUND_LOG}、原支付与退款汇总在 {@code ALIPAY_PAY_LOG}。
 * 本次迁移<b>只换实现宿主、一行表结构与状态口径都没动</b>，
 * <b>NEVER 顺手把它改成落 {@code ALIPAY_REFUND_TXN_DETAIL}</b> —— 那是另一件事，
 * 且会让存量退款单的汇总（按 {@code ALIPAY_REFUND_LOG} 重算）当场失真。
 *
 * <p>实现刻意不带 {@code @Transactional}：链路里有一次支付中心 HTTP 调用，事务包住它会让行锁持有
 * 时长等于对端响应时长；且「出网前那次落库 MUST 已经提交」，否则出网后进程挂掉就什么痕迹都不剩。
 */
public interface AlipayPayRefundService {

    /**
     * 退款申请（{@code POST /internal/alipay/payment/requestRefund}，调用方 fep-alipay，
     * 真实入口是运维 / 管理台的人工退款）。
     *
     * <p>涉及资金（对支付中心发起真实退款），任何改动 MUST 人工复核。
     */
    AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request);
}
