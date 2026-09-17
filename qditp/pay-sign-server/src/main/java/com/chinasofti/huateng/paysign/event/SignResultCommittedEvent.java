package com.chinasofti.huateng.paysign.event;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;

/**
 * 签约成功结果已落库并提交。由 {@code PaySignWorkflow.receiveSignResult} 在事务内发布。
 *
 * @param requestSignSeq 签约流水号
 * @param paymentVendor  支付渠道，回查 {@code APP_PAY_SIGN_INFO} 需要它做联合键
 * @param callback       支付平台签约结果回调原始入参
 */
public record SignResultCommittedEvent(String requestSignSeq,
                                       String paymentVendor,
                                       ReceiveSignResultReqDTO callback) {
}
