package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class F2fOrderExpireService {

    private static final Logger log = LoggerFactory.getLogger(F2fOrderExpireService.class);

    private static final List<String> PENDING = F2fOrderStatus.PENDING;

    private final F2fOrderMapper orderMapper;

    private final F2fPayCenterFlow payCenterFlow;

    private final F2fTvmPayResultService payResultService;

    private final int expireGiveUpHours;

    public F2fOrderExpireService(F2fOrderMapper orderMapper,
                                 F2fPayCenterFlow payCenterFlow,
                                 F2fTvmPayResultService payResultService,
                                 @Value("${f2f.order.expireGiveUpHours:24}") int expireGiveUpHours) {
        this.orderMapper = orderMapper;
        this.payCenterFlow = payCenterFlow;
        this.payResultService = payResultService;
        this.expireGiveUpHours = expireGiveUpHours;
    }

    public boolean reconcileExpiredOrder(F2fOrder order) {
        String orderNo = order.getOrderNo();
        F2fPayCenterFlow.Settled settled = payCenterFlow.settle(new F2fPayCenterFlow.SettleSpec(
                orderNo, PENDING,
                result -> payResultService.markPaymentSuccess(orderNo, result, "过期收口查到支付成功"),
                "过期收口",
                new F2fPayCenterFlow.ExpireOnUnpaid("支付中心无此订单，判定未支付", "二维码超时未支付"),
                (result, paidTms) -> {
                    int updated = orderMapper.markPaid(orderNo, paidTms);
                    log.warn("过期收口发现该订单实际已支付（回调可能丢失）, orderNo={}, updated={}", orderNo, updated);
                    payCenterFlow.enqueuePayResultNotify(orderNo, result.string("orderNo"), paidTms);
                }));
        return switch (settled.settlement()) {
            case PAID, FAILED, UNPAID -> true;
            case PENDING -> false;
        };
    }

    public List<F2fOrder> loadExpiredCandidates(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return orderMapper.selectExpiredCandidates(now.minusHours(expireGiveUpHours), now, limit);
    }

    public long countStaleExpiredOrders() {
        return orderMapper.countStaleExpired(LocalDateTime.now().minusHours(expireGiveUpHours));
    }
}
