package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;

/** 支付域看**支付中心签约/解约方向**的窄接口（防腐层，2026-09-16，ADR-D112）。 */
public interface ContractGatewayPort {

    /** IF8A-16 正式签约（支付中心 §2.1 contract）。 */
    GatewayReply requestContract(RequestSignInfoReqDTO request, String paymentVendor);

    /** IF8A-21 信用能力咨询（支付中心 creditQuery）。 */
    GatewayReply creditQuery(String thirdUserId, String requestSignSeq, String paymentVendor);

    /** IF8A-22 签约结果查询（支付中心 §2.4 contract/queryResult）。内部解约收口也用它。 */
    GatewayReply queryContractResult(String requestSignSeq);

    /** IF8A-06 请求解约（支付中心 §2.3 contract/dismissal）。 */
    GatewayReply requestDismissal(String requestSignSeq);
}
