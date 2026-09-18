package com.chinasofti.huateng.alipay.paysign.controller.paycenter;

import com.chinasofti.huateng.alipay.paysign.service.AlipayPayCallbackService;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付中心方向的回调收口：{@code docs/external/支付中心网关接口文档.md} §5 里对本模块适用的两条。
 *
 * <p>只承载「支付中心 → 本服务」的回调，判断某条回调归属看本类。§5.3 签约结果与 §5.4 解约结果不在此处：
 * 本模块没有配 签约 / 解约 的出向 URL（{@code application.properties} 只有 request-pay / request-refund /
 * pay-query / refund-query 四条），那两条属 {@code pay-sign-server}。
 * 内部约定的两条通知（黑名单变更、业务关闭结果）不在契约 §5 内，留在 {@code controller/AlipayNotifyController}。
 *
 * <p>**包位置是 `controller.paycenter`、不是 `controller`**（2026-09-18 迁入）：迁包只改 Java 包名与文件位置，
 * **URL、Bean 名、入向 DTO 一行未动** —— {@code /api/payment/payNotify} 是已下发给支付中心的回调地址，
 * 包名与它无关。组件扫描根是启动类所在的 {@code ...alipay.paysign}，子包天然被扫到，
 * **NEVER 因为迁了包就去加 `@ComponentScan`**。
 *
 * <p><b>两条端点已于 2026-09-18 切到新接口 {@link AlipayPayCallbackService}</b>（迁移第 5、6 条，
 * 实现在 {@code service.impl.callback} 包）。旧宿主 {@code PaymentQueryService.handlePayNotify} 与
 * {@code PaymentRefundService.handleRefundNotify} 行为一行未改、原样保留作回滚位、现为零调用方，
 * <b>NEVER 在它们上面继续加能力</b>；回滚只需把这里改回注 {@code AlipayTripPaymentService}。
 * 新旧两侧<b>互不调用</b> —— 谁转发给谁都会让「线上跑的是哪一侧」无法判断。
 */
@RestController
@RequestMapping("/api/payment")
public class PayCenterCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PayCenterCallbackController.class);
    private final AlipayPayCallbackService alipayPayCallbackService;

    public PayCenterCallbackController(AlipayPayCallbackService alipayPayCallbackService) {
        this.alipayPayCallbackService = alipayPayCallbackService;
    }

    /**
     * 契约 §5.1 支付结果回调。
     *
     * <p>URL 与入向 DTO 逐字未变（此前只是从 {@code AlipayNotifyController} 挪过来）：这条路径被
     * {@code pay.center.callback-url} 作为回调地址下发给支付中心，改它等于改已在用的对外契约。
     * 入向 DTO 仍是 6 字段、与契约 §5.1 的 11 字段尚未对齐，属已知未闭合项，NEVER 在本类里顺手加字段。
     */
    @PostMapping("/payNotify")
    public AlipayCommonResponse payNotify(@RequestBody AlipayTripPayNotifyReqDTO request) {
        log.info("收到支付回调: orderNo={}", request != null ? request.getOrderNo() : null);
        AlipayCommonResponse response = alipayPayCallbackService.handlePayNotify(request);
        log.info("支付回调响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }

    /**
     * 契约 §5.2 退款结果回调。
     *
     * <p>当前只落回调凭据（{@code CALLBACK_TYPE='REFUND'}）并返成功，退款明细与汇总的回写尚未接线；
     * 因此这条地址还没有配置键、也没下发给支付中心，接线时再一并补。
     */
    @PostMapping("/refundNotify")
    public AlipayCommonResponse refundNotify(@RequestBody AlipayTripRefundNotifyReqDTO request) {
        log.info("收到退款回调: orderNo={}, refundResult={}",
                request != null ? request.getOrderNo() : null,
                request != null ? request.getRefundResult() : null);
        AlipayCommonResponse response = alipayPayCallbackService.handleRefundNotify(request);
        log.info("退款回调响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }
}
