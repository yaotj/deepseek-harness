package com.chinasofti.huateng.facepay.controller.page;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.facepay.api.page.AppPartialRefundRequest;
import com.chinasofti.huateng.facepay.service.F2fPageRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 订单运营端按金额退款，URL 与旧模块 {@code collect-pay-server} 的
 * {@code AppOrderPageController} <b>逐字一致</b>（{@code POST /page/app/orders/{orderNo}/refund}）。
 *
 * <h2>为什么本模块也要有</h2>
 * <p>APP 取票订单已随 2026-09-15 切流落到 {@code F2F_ORDER}（{@code BIZ_TYPE='03'}），
 * 旧端点操作的 {@code TBL_TVM_APP_ORDER} / {@code TBL_APP_ORDER_REFUND} 不再有新数据。
 * 运营后台照旧调这个 URL 时，旧模块只会答「未找到订单」——不是没退成，是根本查不到那张单。
 *
 * <p>本端点与 {@code /page/face-pay/orders/{orderNo}/refund} 的关系是
 * <b>「按指定金额」与「退剩余全额」两种入口，共用同一套闸门与同一张 {@code F2F_REFUND}</b>，
 * 不是两条独立链路。差异只在金额来源，详见
 * {@link F2fPageRefundService#refundByAmount}（含「为什么第二次调用会被幂等拒绝」）。
 *
 * <p><b>鉴权</b>：无，与本模块其余 {@code /page/**} 一致，靠网络隔离。
 * <b>但这条比查询类端点敏感得多 —— 它出钱</b>：任何网络可达方按订单号 POST 一次即可发起退款。
 * 旧实现的类注释也自记了同一条（原文「上线前 MUST 补鉴权」），**上线前 MUST 一并补**。
 */
@RestController
@RequestMapping("/page/app/orders")
public class AppOrderPageController {

    private static final Logger log = LoggerFactory.getLogger(AppOrderPageController.class);

    private final F2fPageRefundService pageRefundService;

    public AppOrderPageController(F2fPageRefundService pageRefundService) {
        this.pageRefundService = pageRefundService;
    }

    /**
     * 按指定金额退款。
     *
     * <p>请求体可空校验放在这里、金额有效性校验放在 service：
     * 前者是「报文有没有」，后者是「业务允不允许」，
     * <b>NEVER 把可退余额判断挪到 controller</b>——那需要读订单与已退汇总，属业务逻辑（AGENTS.md §3.3）。
     *
     * @param request 可空；为空或缺 {@code refundAmount} 时按「金额必须大于 0」拒绝，
     *                <b>NEVER 退化成退全额</b>：本端点的语义就是「按运营指定的金额退」，
     *                把缺失的金额猜成全额是资损路径
     */
    @PostMapping("/{orderNo}/refund")
    public ResultVO<?> refundByAmount(@PathVariable String orderNo,
                                      @RequestBody(required = false) AppPartialRefundRequest request) {
        Long refundAmount = request == null ? null : request.getRefundAmount();
        String reason = request == null ? null : request.getRefundReason();
        String operatorId = request == null ? null : request.getOperatorId();
        log.info("接收到 APP 订单按金额退款请求, orderNo={}, refundAmount={}, operatorId={}",
                orderNo, refundAmount, operatorId);
        if (orderNo == null || orderNo.isBlank()) {
            return ResultMapper.error("订单号不能为空");
        }
        return pageRefundService.refundByAmount(orderNo, refundAmount, reason, operatorId);
    }
}
