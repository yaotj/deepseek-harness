package com.chinasofti.huateng.paysign.port;

import java.util.Map;

/**
 * 支付域看**支付中心退款方向**的窄接口（防腐层，2026-09-16，ADR-D113 续）。
 *
 * <p>收口后 {@code RefundDomainServiceImpl} 的协作者由 4 个降到 3 个 ——
 * {@code PaySignProperties} 与 {@code PaySignGateway} 双双消失（那个类里前者**只**为取两个 URL 存在）。
 *
 * <p><b>bizData 刻意仍由调用点组装并传入</b>，与签约方向不同。理由不是偷懒：
 * {@code requestRefund} 的 bizData 要在出网**之前**落进 {@code PAY_REFUND_DETAIL.REQUEST_BODY}
 * （「留痕 → 出网」那条不变量的物证），它是业务证据链的一部分、不是出向细节；
 * 退款回查那份 bizData 同理带着「两个号都送」的实测结论注释（ADR-D92）。
 * <b>NEVER 为了「对称好看」把它们搬进 adapter</b> —— 那会让落库的报文与真正发出的报文
 * 变成两处各自装配，正是本轮要消灭的形态。
 */
public interface RefundGatewayPort {

    /** 支付 API 3.1 请求退款。bizData 由调用点传入（见接口注释）。 */
    GatewayReply requestRefund(Map<String, Object> bizData);

    /** 支付 API 3.2 退款查询。bizData 由调用点传入（见接口注释）。 */
    GatewayReply queryRefund(Map<String, Object> bizData);

    /**
     * 退款查询地址是否已配置。
     *
     * <p>补偿入口在扫表**之前**要据此整批短路（未配置时停在 PROCESSING 的退款单本轮无人收口，
     * MUST 打 ERROR 并返错，NEVER 静默继续）。<b>暴露一个布尔而不是 URL 本身</b>：
     * 让「URL 是什么」始终只有 adapter 知道。
     */
    boolean refundQueryConfigured();
}
