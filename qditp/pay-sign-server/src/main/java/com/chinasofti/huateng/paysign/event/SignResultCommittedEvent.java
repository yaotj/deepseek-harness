package com.chinasofti.huateng.paysign.event;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;

/**
 * 签约成功结果已落库并提交。由 {@code SignResultCallbackHandler.receiveSignResult} 在事务内发布，
 * 由 {@code SignResultCommittedListener} 在 {@code AFTER_COMMIT} 消费。
 *
 * <p>2026-09-17（ADR-D127）修正了此处的宿主名：原文写的是 {@code PaySignWorkflow.receiveSignResult}，
 * 而那个类已于 ADR-D87 删除，照它去找发布点会一无所获。
 *
 * @param requestSignSeq 签约流水号
 * @param paymentVendor  支付渠道，回查 {@code APP_PAY_SIGN_INFO} 需要它做联合键
 * @param callback       支付平台签约结果回调原始入参
 */
public record SignResultCommittedEvent(String requestSignSeq,
                                       String paymentVendor,
                                       ReceiveSignResultReqDTO callback) {
}
