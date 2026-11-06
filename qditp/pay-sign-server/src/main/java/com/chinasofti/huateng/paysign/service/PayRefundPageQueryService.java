package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper;
import com.chinasofti.huateng.paysign.page.PayRefundOrderView;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 综管台扣费明细页的退款回填查询（{@code /page/pay-refund/by-orders} 的业务层）。
 *
 * <p>只读、不出网、不带事务。形态对齐 face-pay 的 {@code SupplementOrderQueryService}
 * 与 gate-txn-pay 的 {@code GateTxnPayQueryServiceImpl}。
 */
@Service
public class PayRefundPageQueryService {

    /**
     * 单次可查的原支付订单号上限：一页最多 100 行留足余量，
     * 同时远低于 Oracle IN 列表 1000 的硬顶（超了直接 ORA-01795，不是降级而是整条语句失败）。
     */
    private static final int MAX_ORDER_NOS = 200;

    private final PayRefundDetailMapper payRefundDetailMapper;

    public PayRefundPageQueryService(PayRefundDetailMapper payRefundDetailMapper) {
        this.payRefundDetailMapper = payRefundDetailMapper;
    }

    /**
     * 按一批原支付订单号取退款明细。
     *
     * <p>入参先 trim + 去重再判长度：重复单号不该占用配额，也不该让调用方靠「恰好没重复」躲过上限。
     *
     * @return 扁平视图列表；查不到即空列表
     * @throws IllegalArgumentException 列表为空或去重后仍超过 {@value #MAX_ORDER_NOS}
     */
    public List<PayRefundOrderView> byOrderNos(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            throw new IllegalArgumentException("订单号列表不能为空");
        }
        List<String> distinct = orderNos.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            throw new IllegalArgumentException("订单号列表不能为空");
        }
        if (distinct.size() > MAX_ORDER_NOS) {
            throw new IllegalArgumentException("单次最多查询 " + MAX_ORDER_NOS + " 个订单号");
        }
        return payRefundDetailMapper.selectViewsByOrderNos(distinct);
    }
}
