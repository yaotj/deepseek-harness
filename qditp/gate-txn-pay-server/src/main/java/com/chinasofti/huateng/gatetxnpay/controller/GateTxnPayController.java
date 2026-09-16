package com.chinasofti.huateng.gatetxnpay.controller;

import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayQueryService;
import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayService;
import com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 过闸扣费交易入口——**服务间 RPC 调用方向**（fep-dev-server / pay-sign-server /
 * blacklist-server / ticket-server 等）。
 *
 * <p>2026-09-14 按调用方拆分：APP 场景的 6 个 {@code /ci/gateTxnPay/app/*} 已移到
 * {@link com.chinasofti.huateng.gatetxnpay.controller.app.GateTxnPayAppController}，
 * 运营后台在 {@code controller/page}、对账在 {@code controller/internal}。
 * <b>拆分只动文件归属，17 个端点的 URL 一个字符都没改</b>，上游全是硬编码 URL，
 * <b>NEVER 借后续重构改路径</b>。</p>
 *
 * <p>本类保留的 6 个端点里，{@code requestPay} / {@code retryPay} / {@code syncDebitStatus}
 * 会改状态，其余三个只读。</p>
 */
@RestController
@RequestMapping("/ci/gateTxnPay")
public class GateTxnPayController {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayController.class);

    @Autowired
    private GateTxnPayService gateTxnPayService;

    /**
     * 只读查询侧。与 {@code gateTxnPayService} 并列注入，**不是两套实现**：
     * 同一张 `GATE_TXN_PAY`，按「有没有写」拆成两个接口，端点与报文一个都没变。
     */
    @Autowired
    private GateTxnPayQueryService gateTxnPayQueryService;

    @PostMapping("/requestPay")
    public GateTxnPayRespDTO requestPay(@RequestBody GateTxnPayReqDTO request) {
        log.info("接收到过闸扣费交易, 入参={}", request);
        GateTxnPayRespDTO response = gateTxnPayService.requestPay(request);
        log.info("过闸扣费交易处理完成, 返回={}", response);
        return response;
    }

    @PostMapping("/retryPay")
    public GateTxnPayRespDTO retryPay(@RequestBody Map<String, String> request) {
        String orderNo = request != null ? request.get("orderNo") : null;
        log.info("接收到过闸扣费重试支付, orderNo={}", orderNo);
        GateTxnPayRespDTO response = gateTxnPayService.retryPay(orderNo);
        log.info("过闸扣费重试支付处理完成, 返回={}", response);
        return response;
    }

    @PostMapping("/queryOrderByBizKey")
    public GateTxnPayRespDTO queryOrderByBizKey(@RequestBody GateTxnPayReqDTO request) {
        log.info("查询GT订单号, 入参={}", request);
        GateTxnPayRespDTO response = gateTxnPayQueryService.queryOrderByBizKey(request);
        log.info("查询GT订单号完成, 返回={}", response);
        return response;
    }

    // ==================== 解约扣费失败订单查询 RPC ====================

    @PostMapping("/hasFailedOrder")
    public GateTxnPayFailedOrderRespDTO hasFailedOrder(@RequestBody GateTxnPayFailedOrderReqDTO request) {
        log.info("查询解约扣费失败订单, 入参={}", request);
        GateTxnPayFailedOrderRespDTO response = gateTxnPayQueryService.hasFailedOrder(
                request != null ? request.getThirdUserId() : null,
                request != null ? request.getPaymentVendor() : null,
                request != null ? request.getRequestTime() : null);
        log.info("查询解约扣费失败订单完成, 返回={}", response);
        return response;
    }

    /**
     * 按卡号查询是否仍有未结清扣费订单（供 blacklist-server 盘点黑名单可解除性调用）。
     *
     * <p>只读接口，不改任何数据。调用方 MUST 先判断 resultCode 再用 hasUnsettled。</p>
     */
    @PostMapping("/hasUnsettledOrderByCard")
    public CardUnsettledQueryRespDTO hasUnsettledOrderByCard(@RequestBody CardUnsettledQueryReqDTO request) {
        log.info("按卡查询未结清扣费订单, cardId={}", request != null ? request.getCardId() : null);
        CardUnsettledQueryRespDTO response = gateTxnPayQueryService.hasUnsettledOrderByCard(
                request != null ? request.getCardId() : null);
        log.info("按卡查询未结清扣费订单完成, 返回={}", response);
        return response;
    }

    // ==================== 支付结果回调驱动的扣费状态收敛 RPC ====================

    /**
     * pay-sign-server 收到支付中心回调、本地 PAY_TXN_DETAIL 落地成功后调用，
     * 把 GATE_TXN_PAY.DEBIT_STATUS 收敛到终态。
     *
     * <p>只改状态，NEVER 触发扣款；SUCCESS / FAIL 终态订单不会被改写。
     *
     * <p><b>IF8A-26 补款功能已迁移到 face-pay-server（2026-09-15）</b>。
     * 补款单的支付结果由 face-pay 的 PayCenter 回调直接处理，
     * 不再走本入口的补款单分派分支。pay-sign-server 对补款单号的回调
     * 在其侧已做拦截（补款单号不走 pay-sign）。</p>
     */
    @PostMapping("/syncDebitStatus")
    public GateTxnPayRespDTO syncDebitStatus(@RequestBody GateTxnPaySyncStatusReqDTO request) {
        log.info("接收支付结果同步, 入参={}", request);
        GateTxnPayRespDTO response = gateTxnPayService.syncDebitStatus(request);
        log.info("支付结果同步返回={}", response);
        return response;
    }
}
