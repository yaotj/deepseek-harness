package com.chinasofti.huateng.paysign.controller.page;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.paysign.page.PayRefundOrderView;
import com.chinasofti.huateng.paysign.service.PayRefundPageQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 支付域退款运营查询（综管台扣费明细页回填「退款状态 / 退款金额」用）。
 *
 * <p><b>为什么读 {@code PAY_REFUND_DETAIL} 而不是 {@code PAY_TXN_DETAIL}</b>：
 * 2026-11-06 现网实测，闸机扣费成功的 287,906 行里只有 71 行在 {@code PAY_TXN_DETAIL} 有记录
 * （该表按 {@code MERCHANT_ORDER_NO} 关联的是支付中心侧流水，闸机后付费的成功单基本不落它），
 * 而 {@code PAY_REFUND_DETAIL.ORDER_NO} 才是与 {@code GATE_TXN_PAY.ORDER_NO} 同域的键 ——
 * 49 笔退款全部挂在扣费成功的单上，挂在扣费失败的单上是 0 笔。
 * 用错那张表会让这列对运营真正关心的单永远为空。
 */
@RestController
@RequestMapping("/page/pay-refund")
public class PayRefundPageController {

    private final PayRefundPageQueryService payRefundPageQueryService;

    public PayRefundPageController(PayRefundPageQueryService payRefundPageQueryService) {
        this.payRefundPageQueryService = payRefundPageQueryService;
    }

    /**
     * 按一批原支付订单号取退款明细。
     *
     * <p>返回扁平列表、不分页：调用方是「拿当页扣费订单号换退款信息」，在这里分页没有语义。
     * 同一个原单可能有多行（历史上被重复提交过退款），分组与展示口径由调用方决定。
     */
    @GetMapping("/by-orders")
    public ResultVO<List<PayRefundOrderView>> byOrders(@RequestParam(required = false) List<String> orderNos) {
        try {
            return ResultMapper.ok(payRefundPageQueryService.byOrderNos(orderNos));
        } catch (IllegalArgumentException e) {
            return ResultMapper.illegalParams(e.getMessage());
        }
    }
}
