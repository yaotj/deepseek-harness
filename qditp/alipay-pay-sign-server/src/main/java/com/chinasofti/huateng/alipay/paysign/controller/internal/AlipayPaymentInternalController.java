package com.chinasofti.huateng.alipay.paysign.controller.internal;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPayRefundService;
import com.chinasofti.huateng.alipay.paysign.service.impl.payment.AlipayTxnPayQueryService;
import com.chinasofti.huateng.alipay.paysign.service.impl.refund.AlipayTxnRefundService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayTxnBriefDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 支付宝出行支付域的**内部接口控制器**：只有内部调用方的支付类端点都在这里。
 *
 * <p>现承接三条（前两条的 URL 均由 2026-09-18 迁移第 7、8 条搬来，那两步**只搬 URL、不切服务实现**）：
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
 *   <li>{@code POST /internal/alipay/payment/payTxnBrief} —— 2026-09-18 新增，纯只读批量查询，
 *       唯一调用方是支付宝出行乘车记录列表（{@code trans-query-server} 的
 *       {@code AlipayTravelQueryHandler}）的**第二次请求**；服务实现同为
 *       {@link AlipayTxnPayQueryService}。
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
    private final AlipayTxnRefundService alipayTxnRefundService;

    public AlipayPaymentInternalController(AlipayTxnPayQueryService alipayTxnPayQueryService,
                                           AlipayPayRefundService alipayPayRefundService,
                                           AlipayTxnRefundService alipayTxnRefundService) {
        this.alipayTxnPayQueryService = alipayTxnPayQueryService;
        this.alipayPayRefundService = alipayPayRefundService;
        this.alipayTxnRefundService = alipayTxnRefundService;
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

    /**
     * 按订单号列表批量取支付侧字段 —— 支付宝出行乘车记录列表的**第二次请求**。
     *
     * <p><b>入参刻意是裸 JSON 数组</b>（{@code ["GT2026...","GT2026..."]}），不包 DTO：本端点只有
     * 「一页 orderNo」这一个输入、且被有意做窄（见 {@link AlipayTxnPayQueryService#queryPayTxnBrief}
     * 的三条 NEVER）。包一层 DTO 会给「顺手加个 startDate / pageNum」留口子，
     * 那就变成第二个 {@code payLog/travelList} 了。<b>要加维度 MUST 另起端点，NEVER 往这里加字段。</b>
     *
     * <p><b>NEVER 复用 {@code model/app/QueryPayTxnBatchReqDTO}</b> —— 那个是 pay-sign 域
     * IF8A-05 的**对外契约** DTO（会被 fep-app 的 {@code parseBizData} 解析），
     * 内部端点借用它等于把两条不相干的契约焊在一起。
     *
     * <p>出参是**原样的库内值**、命中不到的 orderNo 不补空行，语义与顺序都由 service 说明，
     * 本方法只做日志与路由，<b>NEVER 在 Controller 里做映射或过滤</b>。
     */
    @PostMapping("/payTxnBrief")
    public List<AlipayTripPayTxnBriefDTO> payTxnBrief(@RequestBody List<String> orderNos) {
        log.info("收到批量补齐支付明细请求: 条数={}", orderNos != null ? orderNos.size() : 0);
        List<AlipayTripPayTxnBriefDTO> response = alipayTxnPayQueryService.queryPayTxnBrief(orderNos);
        log.info("批量补齐支付明细响应结果: 命中条数={}", response != null ? response.size() : 0);
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

    /**
     * 退款申请（**读新表 {@code ALIPAY_PAY_TXN_DETAIL}、落 {@code ALIPAY_REFUND_TXN_DETAIL}**）。
     *
     * <p>上面那条 {@code /requestRefund} 读的 {@code ALIPAY_PAY_LOG} 已无写入方，因此它对**任何**新订单都返
     * {@code 9999 原支付记录不存在}（2026-09-20 运营后台实测：分流已生效、请求确实到了本模块，卡在那一步）。
     * 本条是它的替代，由 {@code gate-txn-pay-server} 运营后台退款分流的支付宝那一支调用。
     *
     * <p><b>两条并存、NEVER 删掉任一条</b>：旧的留作回滚位（gate-txn-pay 那一处改回
     * {@code alipayTripRequestRefund} 即可）。<b>也 NEVER 让这条去转发那条</b> —— 两侧读写的表、状态字面量、
     * 单号生成各自独立，互相转发后「线上跑的是哪一侧」就无法判断了。
     *
     * <p>本端点与本族其余端点一样**没有鉴权**，而它既改状态又出网发起真实退款。
     * <b>新增本族端点时 NEVER 把「反正同族都没有鉴权」当成理由</b>，这是待修缺口、不是约定。
     */
    @PostMapping("/requestTxnRefund")
    public AlipayTripTxnRefundRespDTO requestTxnRefund(@RequestBody AlipayTripTxnRefundReqDTO request) {
        log.info("收到退款申请（新表链路）: orderNo={}", request != null ? request.getOrderNo() : null);
        AlipayTripTxnRefundRespDTO response = alipayTxnRefundService.requestRefund(request);
        log.info("退款申请响应结果（新表链路）：retCode={}, retMsg={}, refundOrderNo={}",
                response != null ? response.getRetCode() : "null",
                response != null ? response.getRetMsg() : "null",
                response != null ? response.getRefundOrderNo() : "null");
        return response;
    }
}
