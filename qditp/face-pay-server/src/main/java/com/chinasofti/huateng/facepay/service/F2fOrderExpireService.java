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

/**
 * 已过失效时间订单的收口：扫候选、逐笔问支付中心、按答复决定是否置 {@code EXPIRED}。
 *
 * <p>2026-09-16 从 {@code F2fTvmOrderService} 拆出（原 {@code reconcileExpiredOrder} /
 * {@code loadExpiredCandidates} / {@code countStaleExpiredOrders} 三个方法），
 * <b>逻辑一行未改</b>。唯一调用方是 {@link com.chinasofti.huateng.facepay.scheduler.F2fOrderExpireJob}。</p>
 *
 * <p><b>它服务的不只是 TVM</b>：{@code selectExpiredCandidates} 按 {@code EXPIRE_TMS} 扫全表，
 * TVM 拉码单（180 秒）、BOM 柜台单（30 分钟）、APP 取票单都在内。这也是把它从
 * {@code F2fTvmOrderService} 挪出来的判据 —— 留在那个类里会让「TVM 下单」看着像它的宿主域。</p>
 *
 * <p>本类<b>不带 {@code @Transactional}</b>，且 MUST 保持如此：{@code reconcileExpiredOrder}
 * 中间那次 {@code execute} 是网络调用（AGENTS.md §5.2）。</p>
 */
@Service
public class F2fOrderExpireService {

    private static final Logger log = LoggerFactory.getLogger(F2fOrderExpireService.class);

    /** 需要向支付中心查实际结果的状态白名单，也是这里两处 CAS 的前置。 */
    private static final List<String> PENDING = F2fOrderStatus.PENDING;

    private final F2fOrderMapper orderMapper;

    /** 查询 + 分支判定 + EXPIRED 收口都在它里面（P3 之后本类不再直连支付中心）。 */
    private final F2fPayCenterFlow payCenterFlow;

    /**
     * 「查到其实已支付」时的支付流水收口，与设备查询链路共用同一份实现。
     *
     * <p>拆分时刻意<b>不</b>在本类再抄一份 {@code markPaymentSuccess}：那 15 行含
     * {@code UK_F2F_PAY_SUCCESS} 幂等与 cause 链判定，抄两份必然漂移。
     * 判据写在 {@link F2fTvmPayResultService#markPaymentSuccess} 的方法注释里。</p>
     */
    private final F2fTvmPayResultService payResultService;

    /**
     * 过期收口的放弃窗口（小时）。超过它仍未收口的订单不再被扫、不再外呼，只计数告警。
     *
     * <p>与 {@code F2fRefundReconcileJob} 的 {@code RETRY_TIMES < 20} 是同一类闸门，
     * 只是这里没有重试计数列，用时间窗等价表达。</p>
     */
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
     * 二维码已过期订单的收口：<b>先问支付中心，再决定是否置 EXPIRED</b>。
     *
     * <p>不能盲目按时间置过期——{@code PAYING} 意味着码已经给出去了，乘客可能刚付完而回调还没到。
     * 因此这里的顺序是：查支付中心 →</p>
     * <ul>
     *   <li>{@code SUCCESS} → {@code markPaid}，相当于补救一次丢失的回调；</li>
     *   <li>业务码非 0（支付中心没有这笔单）/ {@code FAILED} / {@code UNPAID} → 置 {@code EXPIRED}；</li>
     *   <li><b>传输失败或状态仍在处理中 → 什么都不做</b>，留给下一轮扫表。NEVER 在这里置终态。</li>
     * </ul>
     *
     * <p><b>2026-09-16（P3）起，查询与分支判定收口进 {@link F2fPayCenterFlow#settle}</b>，
     * 本方法不再自己 {@code execute} 支付中心。两处曾经只存在于本方法的语义，
     * 现在由 {@code SettleSpec} 的两个可选字段显式表达、<b>NEVER 丢</b>：
     * ① {@code expireOnUnpaid} 把「业务码非 0」与「明确支付失败」都判成 {@code EXPIRED}
     * （设备查询链路对这两种情况分别回「支付中」与 {@code PAY_FAILED}，与这里刻意不同）；
     * ② {@code onPaidNotify} 保住本支「先 {@code markPaid} 再按支付中心订单号入队 IF8B-05」
     * 的写法 —— 它与 {@code markPaidAndReport} 的日志和 {@code tradeNo} 取值都不同。</p>
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
                    // IF8B-05 支付结果通知：本分支与 markPaidAndReport 的语义/日志都不同，
                    // NEVER 替换成 markPaidAndReport。tradeNo 取支付中心订单号
                    // （与 markPaymentSuccess 落 PAY_CENTER_ORDER_NO 用的是同一个值）。
                    payCenterFlow.enqueuePayResultNotify(orderNo, result.string("orderNo"), paidTms);
                }));
        return switch (settled.settlement()) {
            case PAID, FAILED, UNPAID -> true;
            // 传输失败或支付中心仍报处理中：本轮不算收口，下轮再试（放弃窗口由扫表下界兜）。
            case PENDING -> false;
        };
    }

    /**
     * 扫一批已过期候选订单，供定时任务调用。
     *
     * <p>只捞 {@code EXPIRE_TMS} 落在 {@code [now - expireGiveUpHours, now)} 内的单。
     * <b>下界是放弃窗口，NEVER 去掉</b>——见 {@link F2fOrderMapper#selectExpiredCandidates}。</p>
     */
    public List<F2fOrder> loadExpiredCandidates(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return orderMapper.selectExpiredCandidates(now.minusHours(expireGiveUpHours), now, limit);
    }

    /** 已过放弃窗口、仍未收口的订单数，供任务打告警。 */
    public long countStaleExpiredOrders() {
        return orderMapper.countStaleExpired(LocalDateTime.now().minusHours(expireGiveUpHours));
    }
}
