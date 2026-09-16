package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;

/**
 * 支付域看**支付中心签约/解约方向**的窄接口（防腐层，2026-09-16，ADR-D112）。
 *
 * <p><b>它是 {@link AccountDomainPort} 的对称另一半</b>。那个端口的类注释里写着账户方向改造前的病症：
 * 「每个调用点各自装配 DTO 并各写一遍 retCode 判定；要写单测就得 mock client 和一串 rpc DTO」。
 * <b>同一条理由在出向支付中心这个方向上一直没被应用过</b> —— `port/` 下只有账户方向的端口，
 * 而支付中心方向仍是裸的 {@code paySignGateway} + {@code paySignProperties}：
 * 「哪个 URL、组哪份 bizData、怎么判成功」在 {@code ContractDomainServiceImpl} 里重复 5 次。
 *
 * <p><b>本接口按用例一个方法，NEVER 退化成 {@code call(url, bizData)}</b>：那样等于把
 * {@code paySignGateway} 换个名字，URL 与报文装配又回到调用点。<b>URL 只允许出现在
 * {@link ContractGatewayAdapter} 里一处</b>（AGENTS.md §8「NEVER 退回拼接」的同源约束：
 * 路径带 {@code /v1}，散开后漏一处不报 404、极难定位）。
 *
 * <p><b>方法一律返回 {@link GatewayReply}</b>，调用点用模式匹配处置，不再自己调 {@code isSuccess}。
 * 实现方 <b>NEVER 抛异常</b>（沿用 {@code PayGatewayClient} 现有行为）。
 */
public interface ContractGatewayPort {

    /**
     * IF8A-16 正式签约（支付中心 §2.1 contract）。
     *
     * <p>回调地址的三级回落（报文 {@code notifyUrl} → 配置默认值 → {@code returnUrl}）
     * <b>在实现内完成</b>：它要读 {@code PaySignProperties}，留在领域服务里就是那个类
     * 仍然依赖 properties 的唯一原因。
     */
    GatewayReply requestContract(RequestSignInfoReqDTO request, String paymentVendor);

    /** IF8A-21 信用能力咨询（支付中心 creditQuery）。 */
    GatewayReply creditQuery(String thirdUserId, String requestSignSeq, String paymentVendor);

    /** IF8A-22 签约结果查询（支付中心 §2.4 contract/queryResult）。内部解约收口也用它。 */
    GatewayReply queryContractResult(String requestSignSeq);

    /** IF8A-06 请求解约（支付中心 §2.3 contract/dismissal）。 */
    GatewayReply requestDismissal(String requestSignSeq);
}
