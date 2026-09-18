package com.chinasofti.huateng.alipay.paysign.controller.payment;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPayRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行（小程序）支付入口 —— 落新表 {@code ALIPAY_PAY_TXN_DETAIL} 的链路。
 *
 * <p>现只承接扣费申请 {@code POST /api/payment/requestPay} 一个端点。
 *
 * <p>与 {@code controller.sign} / {@code controller.paycenter} 同一分包维度（按业务域分），
 * 服务实现在对应的 {@code service.impl.payment} / {@code service.impl.pay} 包下。
 *
 * <p><b>{@code requestPay} 的 URL 与旧实现逐字相同</b>，上游 {@code gate-txn-pay-server} 的
 * {@code PaySignInitiator} 与 {@code rpc} 的 {@code AlipayPaySignClient} 都不需要改一行。
 *
 * <p><b>因此 {@code AlipayTripPaymentController} 里那个同名端点 MUST 保持注释状态</b>：
 * Spring MVC 不允许两个 handler 注册同一个「方法 + 路径」，两边都放开会在启动时直接抛
 * {@code Ambiguous mapping}，而且是**启动即挂**、不是运行时才发现。
 *
 * <p><b>{@code requestPay} 已于 2026-09-18 切到新接口 {@link AlipayPayRequestService}</b>
 * （迁移第 4 条，实现在 {@code service.impl.pay} 包）。{@code AlipayTxnPayService} 原样保留作回滚位、
 * 现为零调用方，<b>NEVER 在它上面继续加能力</b>；回滚只需把这里改回注它。
 * 两侧<b>互不调用</b> —— 谁转发给谁都会让「线上跑的是哪一侧」无法判断。
 *
 * <p><b>{@code payQuery} 已于 2026-09-18 迁出本类</b>（迁移第 7 条）：现宿主是
 * {@code controller/internal/AlipayPaymentInternalController}，URL 改为
 * {@code POST /internal/alipay/payment/payQuery}，旧路径**已删除、无别名**。
 * 服务实现仍是 {@code AlipayTxnPayQueryService}、一行未改。
 * <b>NEVER 在本类把 {@code payQuery} 加回来</b> —— 与旧 URL 并存等于让「调用方打的是哪条」无法判断，
 * 而 hard_switch 的前提就是同批滚更 {@code fep-alipay}。
 */
@RestController
@RequestMapping("/api/payment")
public class AlipayTxnPayController {

    private static final Logger log = LoggerFactory.getLogger(AlipayTxnPayController.class);

    private final AlipayPayRequestService alipayPayRequestService;

    public AlipayTxnPayController(AlipayPayRequestService alipayPayRequestService) {
        this.alipayPayRequestService = alipayPayRequestService;
    }

    /** 扣费申请。 */
    @PostMapping("/requestPay")
    public AlipayTripRequestPayRespDTO requestPay(@RequestBody AlipayTripRequestPayReqDTO request) {
        log.info("收到支付申请（新链路）: orderNo={}", request != null ? request.getOrderNo() : null);
        AlipayTripRequestPayRespDTO response = alipayPayRequestService.requestPay(request);
        log.info("支付申请响应结果（新链路）：retCode={}", response != null ? response.getRetCode() : "null");
        return response;
    }
}
