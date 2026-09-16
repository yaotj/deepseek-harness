package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.model.app.RequestPayReqDTO;

/**
 * 支付域看**支付中心免密扣款方向**的窄接口（防腐层，2026-09-16，ADR-D113 续）。
 *
 * <p>与 {@link ContractGatewayPort} 同一条路子：URL、报文装配、应答判读收进
 * {@link PaymentGatewayAdapter} 一处，领域服务不再注 {@code PaySignGateway}。
 * {@code PaySignProperties} 仍留在 {@code PaymentDomainServiceImpl}，
 * 因为那里还有一个与出网无关的 {@code getTestForceAmount}（测试用强制金额）——
 * <b>这是预期的，NEVER 为了「凑齐 0 个 properties」把它也搬进端口。</b>
 */
public interface PaymentGatewayPort {

    /**
     * 支付 API 1.1 免密扣款。
     *
     * <p>回调地址回落（报文 {@code notifyUrl} → 支付专用配置）与 bizData 装配都在实现内完成。
     */
    PaymentReply requestPay(RequestPayReqDTO request);

    /**
     * 支付 API 查询支付状态，**只用于「拉黑前二次确认」**。
     *
     * <p><b>URL 未配置时返回 {@link GatewayReply.Rejected}（并在实现内打 ERROR）</b>，
     * 调用点据此按「不拉黑」处理 —— 与收口前逐字同义。
     */
    GatewayReply queryPayStatus(String merchantOrderNo);
}
