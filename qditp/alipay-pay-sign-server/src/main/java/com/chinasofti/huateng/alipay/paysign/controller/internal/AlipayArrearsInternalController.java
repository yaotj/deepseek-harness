package com.chinasofti.huateng.alipay.paysign.controller.internal;

import com.chinasofti.huateng.alipay.paysign.service.impl.query.AlipayArrearsQueryService;
import com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行欠费内部只读接口。
 *
 * <p><b>2026-09-20 迁入 {@code controller.internal} 子包</b>（迁移第 14 条）：<b>URL、Bean 名、类名逐字未动</b>
 * （类级前缀仍是 {@code /internal/alipayPay}，方法仍是 {@code POST /hasUnsettledOrderByCard}）——
 * Spring 映射只看注解、与包路径无关，{@code rpc/AlipayPaySignClient.hasUnsettledOrderByCard} 不需要改一行。
 *
 * <p><b>本类的调用方是「服务间」的，不是 Quartz</b>：blacklist-server 的
 * {@code BlacklistReleaseInspectService.queryAlipay} 在盘点黑名单可解除性时逐条问过来
 * （那条链路的**源头**是 web-admin `sys_job` 280（2026-09-21 由 105 改号为 280），但直接调用方是 blacklist-server）。
 * 因此本类留在 {@code controller.internal}，<b>NEVER 因为源头是 Quartz 就把它搬进 {@code controller.task}</b>
 * —— 那个包的判据是「直接调用方只有 Quartz、没有服务间调用方」（详见 {@code controller.task} 包内注释）。
 *
 * <p><b>类级前缀 {@code /internal/alipayPay} 是驼峰、少一层，与同模块另三个 {@code /internal/alipay/**} 不一致</b>，
 * 属历史形态。<b>NEVER 顺手改成 {@code /internal/alipay/arrears}</b> —— 那是在用的对内契约，
 * 改它要与 {@code rpc} 侧同批滚更，本轮迁包刻意不动 URL。
 *
 * <p><b>本端点只读、当前无鉴权</b>，与同模块另三个 {@code /internal/**} 同现状（测试期既有降级）。
 * <b>NEVER 在本前缀下新增写接口而不补验签</b>：类注释把「只读」当成免鉴权的理由，一旦加了写接口该理由即失效。
 *
 * <p>下游语义 MUST 记住：查询未真正执行时本接口会把 {@code hasUnsettled} 置 {@code true} 并给非 {@code 0000}
 * 的 {@code resultCode}，调用方 MUST 先判 {@code resultCode}，<b>NEVER 把 true 当成「有欠费」、
 * 也 NEVER 把 false 当成「已结清」</b>（放行判定宁可拦住）。
 */
@RestController
@RequestMapping("/internal/alipayPay")
public class AlipayArrearsInternalController {

    private static final Logger log = LoggerFactory.getLogger(AlipayArrearsInternalController.class);

    private final AlipayArrearsQueryService alipayArrearsQueryService;

    public AlipayArrearsInternalController(AlipayArrearsQueryService alipayArrearsQueryService) {
        this.alipayArrearsQueryService = alipayArrearsQueryService;
    }

    /**
     * 按卡号查询支付宝出行链路是否仍有未结清订单（供 blacklist-server 盘点调用）。
     */
    @PostMapping("/hasUnsettledOrderByCard")
    public CardUnsettledQueryRespDTO hasUnsettledOrderByCard(@RequestBody CardUnsettledQueryReqDTO request) {
        log.info("按卡查询支付宝出行未结清订单, cardId={}", request != null ? request.getCardId() : null);
        CardUnsettledQueryRespDTO response = alipayArrearsQueryService.hasUnsettledOrderByCard(
                request != null ? request.getCardId() : null);
        log.info("按卡查询支付宝出行未结清订单完成, 返回={}", response);
        return response;
    }
}
