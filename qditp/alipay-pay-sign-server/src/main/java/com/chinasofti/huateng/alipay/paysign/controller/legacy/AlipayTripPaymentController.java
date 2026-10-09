package com.chinasofti.huateng.alipay.paysign.controller.legacy;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.impl.payment.PaymentQueryService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行支付 Controller —— **现已收窄为只剩 {@code findTravelDetail} 一个活端点**。
 *
 * <p><b>2026-09-20 迁入 {@code controller.legacy} 子包</b>（迁移第 13 条，**按用户裁决**）：
 * 本类是「一个零调用方端点 + 三段回滚位注释块」的历史残壳。<b>URL 与 Bean 名逐字未动</b>，
 * Spring 映射只看注解、与包路径无关。组件扫描根是启动类所在的 {@code ...alipay.paysign}，
 * 子包天然被扫到，<b>NEVER 因为迁了包就去加 {@code @ComponentScan}</b>。
 *
 * <p><b>`findTravelDetail` 全仓零调用方</b>（2026-09-20 复核）：{@code rpc} 里没有指向本服务这条路径的方法
 * （{@code TicketClient.alipayTripFindTravelDetail} 打的是 ticket-server 的同名路径、
 * {@code TransQueryClient.findTravelDetail} 打的是 trans-query-server），
 * <b>真实在跑的行程详情链路是 fep-alipay → {@code TransQueryClient} → trans-query-server
 * 的 {@code AlipayTravelQueryHandler}</b>（只读 {@code GATE_TXN_PAY} + {@code ALIPAY_PAY_TXN_DETAIL}）。
 * 本类这份实现读的是旧表 {@code ALIPAY_PAY_LOG}，**NEVER 为了「统一」给它加一次 gate RPC**（ADR-D137 / :7324）。
 *
 * <p>类内另外三个方法都是**整段注释掉的回滚位**，各自的迁出去向与「为什么不能放开」的理由
 * 写在对应注释块里：{@code requestPay} 迁至 {@code AlipayTxnPayController}（URL 未变），
 * {@code payQuery} 与 {@code requestRefund} 于 2026-09-18 迁至
 * {@code AlipayPaymentInternalController} 的 {@code /internal/alipay/payment/**}（旧 URL 已删除）。
 * <b>这两类回滚位的约束不同</b>：requestPay 那条放开会 `Ambiguous mapping` 启动即挂，
 * 后两条的路径现在确实空着、放开只违反 hard_switch 口径 —— 改动前 MUST 读清各自那段。
 * <b>那三段注释块逐字保留、MUST 保持注释状态</b>，迁包过程一个字都没动。
 *
 * <p>支付结果回调等入向通知已迁至 {@code controller/legacy/AlipayNotifyController}（内部约定两条）
 * 与 {@code controller/paycenter/PayCenterCallbackController}（支付中心契约 §5 两条）。
 * {@code findTravelDetail} 从签约 Controller 迁入本类时，URL 由 {@code /channel/findTravelDetail}
 * 改为 {@code /api/payment/findTravelDetail}，因其零调用方、改前缀不影响任何在跑的链路。
 *
 * <p><b>类级前缀 {@code /api/payment} NEVER 改</b>：同前缀下 {@code PayCenterCallbackController}
 * 挂着真正对外的 {@code payNotify}，那条路径由 {@code pay.center.callback-url} 下发给支付中心。
 *
 * <p><b>本包现有四类、判据各不相同、NEVER 混为一谈</b>（详见 {@link AlipayPaySignController} 类注释）：
 * ①{@code AlipayPaySignController} 零调用方待删；②{@link AlipayNotifyController} 有真实在跑的调用方；
 * ③{@link AlipayPayLogController} 零调用方但是旧表存量数据的唯一读出口、不能删；
 * ④本类 = 零调用方 + 三段回滚位注释块，**删它前 MUST 先确认那三段回滚位不再需要**。
 *
 * <p><b>2026-09-21 起本类直接注 {@link PaymentQueryService}、不再经 {@code AlipayTripPaymentService}</b>
 * （拆门面第 1 步）：那个门面同时横跨支付申请 / 查询 / 退款 / 通知四类聚合，而本类只剩查询一条，
 * 经它转发纯属多一跳。{@code findTravelDetail} 的实现体、URL、Bean 名与返回全部逐字未动，<b>行为零变化</b>。
 * <b>连带约束：下面三段回滚位注释块里的方法体引用的是已被移除的 {@code alipayTripPaymentService} 字段</b>，
 * 真要回滚 MUST 同批把那个字段与构造注入一起加回来（门面类与旧实现都按裁决保留着、随时可注），
 * <b>NEVER 只放开注释就以为能编译</b>。
 */
@RestController
@RequestMapping("/api/payment")
public class AlipayTripPaymentController {

    private static final Logger log = LoggerFactory.getLogger(AlipayTripPaymentController.class);
    private final PaymentQueryService paymentQueryService;

    public AlipayTripPaymentController(PaymentQueryService paymentQueryService) {
        this.paymentQueryService = paymentQueryService;
    }

    /*
     * 【支付申请端点已迁至 AlipayTxnPayController，本方法 MUST 保持注释状态】
     *
     * POST /api/payment/requestPay 现由 AlipayTxnPayController 承接，走落新表
     * ALIPAY_PAY_TXN_DETAIL 的 AlipayTxnPayService。URL 逐字未变，上游 gate-txn-pay-server 与
     * rpc 的 AlipayPaySignClient 都不需要改。
     *
     * NEVER 把本方法放开：Spring MVC 不允许两个 handler 注册同一个「方法 + 路径」，两边同时存在会在
     * 启动时抛 Ambiguous mapping —— 启动即挂，不是运行时才发现。
     *
     * 要回退到旧链路，就把 AlipayTxnPayController 那个方法注掉、再放开本方法；
     * 旧实现（AlipayTripPaymentService.requestPay -> PaymentRequestService）逐字保留、零改动。
     *
     * @PostMapping("/requestPay")
     * public AlipayTripRequestPayRespDTO requestPay(@RequestBody AlipayTripRequestPayReqDTO request) {
     *     log.info("收到支付申请: orderNo={}", request != null ? request.getOrderNo() : null);
     *     AlipayTripRequestPayRespDTO response = alipayTripPaymentService.requestPay(request);
     *     log.info("支付申请响应结果：{}", response != null ? response.getRetMsg() : "null");
     *     return response;
     * }
     */

    /*
     * 【支付结果查询端点已迁至 internal 前缀，本方法 MUST 保持注释状态】
     *
     * 支付结果查询现由 AlipayPaymentInternalController 承接，路径是
     * POST /internal/alipay/payment/payQuery（2026-09-18 由 POST /api/payment/payQuery 迁入），
     * 走落新表 ALIPAY_PAY_TXN_DETAIL 的 AlipayTxnPayQueryService。
     * rpc 的 AlipayPaySignClient.alipayTripPayQuery 已同步改成新路径。
     *
     * NEVER 把本方法放开：旧路径 /api/payment/payQuery 已按 hard_switch 裁决整条删除、不留别名，
     * 放开它等于把一条已经没人打的 URL 复活，反而让「调用方打的是哪条」无法判断。
     * 注意这里的理由与上面 requestPay 那段不同 —— requestPay 是「同路径两个 handler 会 Ambiguous mapping」，
     * 而 payQuery 现在这条路径确实空着，编译与启动都不会报错，唯一的约束是上面这条口径。
     *
     * 要回退到旧链路，MUST 三处一起改：放开本方法、注掉 AlipayPaymentInternalController 的 handler、
     * 把 rpc 的路径改回去；旧实现（AlipayTripPaymentService.payQuery -> PaymentQueryService.payQuery）
     * 逐字保留、零改动。
     *
     * @PostMapping("/payQuery")
     * public AlipayTripPayQueryRespDTO payQuery(@RequestBody AlipayTripPayQueryReqDTO request) {
     *     log.info("收到支付查询: orderNo={}", request != null ? request.getOrderNo() : null);
     *     AlipayTripPayQueryRespDTO response = alipayTripPaymentService.payQuery(request);
     *     log.info("支付查询响应结果：{}", response != null ? response.getRetMsg() : "null");
     *     return response;
     * }
     */

    /*
     * 【退款申请端点已迁至 internal 前缀，本方法 MUST 保持注释状态】
     *
     * 退款申请现由 AlipayPaymentInternalController 承接，路径是
     * POST /internal/alipay/payment/requestRefund（2026-09-18 由 POST /api/payment/requestRefund 迁入，
     * 迁移第 8 条）。迁移理由：该端点只有内部调用方（rpc 的 AlipayPaySignClient.alipayTripRequestRefund
     * ← fep-alipay AlipayPaymentServiceImpl），真实入口是运维/管理台的人工退款。
     * 服务实现一行未改，仍是 AlipayTripPaymentService.requestRefund -> PaymentRefundService（落旧表
     * ALIPAY_PAY_LOG）—— 退款方向从来没有落新表的第二套实现。
     *
     * NEVER 把本方法放开：旧路径 /api/payment/requestRefund 已按 hard_switch 裁决整条删除、不留别名。
     * 与下面 payQuery 那段同理，这条路径现在确实空着、编译与启动都不会报错，约束只在这条口径上。
     *
     * 要回退，MUST 三处一起改：放开本方法、注掉 AlipayPaymentInternalController 的 handler、
     * 把 rpc 的路径改回去，并与 fep-alipay 同批滚更。
     *
     * @PostMapping("/requestRefund")
     * public AlipayTripRequestRefundRespDTO requestRefund(@RequestBody AlipayTripRequestRefundReqDTO request) {
     *     log.info("收到退款申请: orderNo={}", request != null ? request.getOrderNo() : null);
     *     AlipayTripRequestRefundRespDTO response = alipayTripPaymentService.requestRefund(request);
     *     log.info("退款申请响应结果：{}", response != null ? response.getRetMsg() : "null");
     *     return response;
     * }
     */

    /**
     * 支付宝出行-查询乘车记录详情。
     */
    @PostMapping("/findTravelDetail")
    public AlipayTripFindTravelDetailRespDTO findTravelDetail(@RequestBody AlipayTripFindTravelDetailReqDTO request) {
        log.info("查询乘车记录详情: orderNo={}", request != null ? request.getOrderNo() : null);
        AlipayTripFindTravelDetailRespDTO response = paymentQueryService.findTravelDetail(request);
        log.info("查询乘车记录详情响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }
}
