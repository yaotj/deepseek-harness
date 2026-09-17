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

/** 已过失效时间订单的收口：扫候选、逐笔问支付中心、按答复决定是否置 {@code EXPIRED}。本类刻意不带 {@code @Transactional}（链路里有支付中心调用），NEVER 加。 */
@Service
public class F2fOrderExpireService {

    private static final Logger log = LoggerFactory.getLogger(F2fOrderExpireService.class);

    /** 需要向支付中心查实际结果的状态白名单，也是这里两处 CAS 的前置。 */
    private static final List<String> PENDING = F2fOrderStatus.PENDING;

    private final F2fOrderMapper orderMapper;

    /** 查询 + 分支判定 + EXPIRED 收口都在它里面（P3 之后本类不再直连支付中心）。 */
    private final F2fPayCenterFlow payCenterFlow;

    /** 「查到其实已支付」时的支付流水收口，与设备查询链路共用同一份实现。 */
    private final F2fTvmPayResultService payResultService;

    /** 过期收口的放弃窗口（小时）。 */
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

    /**
     * 二维码已过期订单的收口：先问支付中心，再决定是否置 EXPIRED。
     *
     * @return true 表示本轮已收口（无需再扫），false 表示状态不明、下轮重试
     */
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

    /** 扫一批已过期候选订单，供定时任务调用。 */
    public List<F2fOrder> loadExpiredCandidates(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return orderMapper.selectExpiredCandidates(now.minusHours(expireGiveUpHours), now, limit);
    }

    /** 已过放弃窗口、仍未收口的订单数，供任务打告警。 */
    public long countStaleExpiredOrders() {
        return orderMapper.countStaleExpired(LocalDateTime.now().minusHours(expireGiveUpHours));
    }
}
