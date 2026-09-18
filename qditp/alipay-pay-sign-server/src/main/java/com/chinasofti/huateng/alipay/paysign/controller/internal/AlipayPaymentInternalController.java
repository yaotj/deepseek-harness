package com.chinasofti.huateng.alipay.paysign.controller.internal;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPayRefundService;
import com.chinasofti.huateng.alipay.paysign.service.impl.payment.AlipayTxnPayQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行支付域的**内部接口控制器**：只有内部调用方的支付类端点都在这里。
 *
 * <p>现承接两条（URL 均由 2026-09-18 迁移第 7、8 条搬来，那两步**只搬 URL、不切服务实现**）：
 * <ul>
 *   <li>{@code POST /internal/alipay/payment/payQuery} ← 原 {@code POST /api/payment/payQuery}，
 *       服务实现仍是 {@link AlipayTxnPayQueryService}（落新表 {@code ALIPAY_PAY_TXN_DETAIL} 那一份）；
 *   <li>{@code POST /internal/alipay/payment/requestRefund} ← 原 {@code POST /api/payment/requestRefund}，
 *       服务实现**已于同日迁移第 9 条切到** {@link AlipayPayRefundService}
 *       （实现 {@code service.impl.refund.AlipayPayRefundServiceImpl}）。
 *       旧门面 {@code AlipayTripPaymentService#requestRefund} → {@code PaymentRefundService} 原样保留作回滚位、
 *       现为零调用方。<b>迁移第 9 条只换实现宿主：表、状态取值、响应文案与出网 bizData 键名一行未改</b>，
 *       退款方向落的仍是**旧表** {@code ALIPAY_PAY_LOG} + {@code ALIPAY_REFUND_LOG} ——
 *       <b>退款方向从来没有落新表的第二套实现</b>。
 * </ul>
 *
 * <p><b>同日本类由 {@code controller} 根包迁入 {@code controller.internal} 子包</b>（并由
 * {@code AlipayPayQueryInternalController} 改名为现名 —— `requestRefund` 进来后旧类名已名不副实）：
 * 只改 Java 包声明与类名，**类级 `@RequestMapping` 前缀与两条 URL 全都没动**，
 * 因此这一步**零契约影响、不需要同批滚更任何上游**。组件扫描根是启动类所在的
 * {@code ...alipay.paysign}，子包天然被扫到，**NEVER 因为迁了包就去加 `@ComponentScan`**。
 *
 * <p>两条的调用方都是唯一的、且都在内网：{@code rpc/AlipayPaySignClient} 的
 * {@code alipayTripPayQuery} / {@code alipayTripRequestRefund} ← fep-alipay 的
 * {@code AlipayQueryServiceImpl} / {@code AlipayPaymentServiceImpl}。
 * 对外契约面在 fep-alipay 的 {@code POST /admin/payment/payQuery} 与
 * {@code POST /admin/payment/requestRefund} 上，<b>那两条 URL 未变</b>。
 * {@code requestRefund} 的真实入口是运维/管理台的人工退款，不是乘客侧自助。
 *
 * <p><b>为什么当初不把 {@code AlipayTxnPayController} 整类改前缀</b>：
 * {@code /api/payment} 下还有真正对外的 {@code payNotify} ——
 * 那条路径由 {@code pay.center.callback-url} 下发给支付中心，改它等于改在用的对外契约。
 * 因此**只能改方法级路径，NEVER 整类改前缀**。
 *
 * <p><b>URL 迁移那一步旧路径一律直接删除、不留别名</b>（用户裁决 hard_switch）。因此当时
 * {@code alipay-pay-sign-server} 与 {@code fep-alipay} 两个镜像 <b>MUST 同批滚更</b> ——
 * 任一侧先上，另一侧打的就是已不存在的路径，而本项目的 404 会被伪装成
 * HTTP 200 + UUID retCode、静默不报（见 ADR-D137）。
 *
 * <p><b>迁到 internal 只是结构收敛，不承担任何安全语义</b>：本模块无拦截器、无验签，
 * 该族全部裸暴露。{@code requestRefund} 是本族里敞口最大的一条 ——
 * 它既改状态又出网到支付中心发起真实退款，按 AGENTS.md §5.2 本应有鉴权与归属校验，
 * <b>迁移前后都不满足</b>。这是既有缺口、不是本次引入的，但**新增本族端点时 NEVER 把
 * 「反正同族都没有鉴权」当成理由**。
 */
@RestController
@RequestMapping("/internal/alipay/payment")
public class AlipayPaymentInternalController {

    private static final Logger log = LoggerFactory.getLogger(AlipayPaymentInternalController.class);

    private final AlipayTxnPayQueryService alipayTxnPayQueryService;
    private final AlipayPayRefundService alipayPayRefundService;

    public AlipayPaymentInternalController(AlipayTxnPayQueryService alipayTxnPayQueryService,
                                           AlipayPayRefundService alipayPayRefundService) {
        this.alipayTxnPayQueryService = alipayTxnPayQueryService;
        this.alipayPayRefundService = alipayPayRefundService;
    }

    /** 支付结果查询。 */
    @PostMapping("/payQuery")
    public AlipayTripPayQueryRespDTO payQuery(@RequestBody AlipayTripPayQueryReqDTO request) {
        log.info("收到支付查询（新链路）: orderNo={}", request != null ? request.getOrderNo() : null);
        AlipayTripPayQueryRespDTO response = alipayTxnPayQueryService.payQuery(request);
        log.info("支付查询响应结果（新链路）：retCode={}, tradeStatus={}",
                response != null ? response.getRetCode() : "null",
                response != null ? response.getTradeStatus() : "null");
        return response;
    }

    /** 退款申请（运维侧人工发起）。日志文案与迁移前逐字一致，便于按日志比对新旧链路。 */
    @PostMapping("/requestRefund")
    public AlipayTripRequestRefundRespDTO requestRefund(@RequestBody AlipayTripRequestRefundReqDTO request) {
        log.info("收到退款申请: orderNo={}", request != null ? request.getOrderNo() : null);
        AlipayTripRequestRefundRespDTO response = alipayPayRefundService.requestRefund(request);
        log.info("退款申请响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }
}
