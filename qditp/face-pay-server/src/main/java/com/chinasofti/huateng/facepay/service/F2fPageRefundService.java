package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.entity.F2fRefund;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import com.chinasofti.huateng.facepay.mapper.F2fRefundMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营端人工退款。返回 {@link ResultVO}（管理后台形态），不是设备的 retCode 契约。
 *
 * <h2>与旧实现的差异</h2>
 * <ul>
 *   <li><b>「已退过」的判定不再看 {@code RSV2} 是否非空。</b>旧实现用订单表的
 *       {@code RSV2}（复用字段）存退款单号来判重，这依赖退款成功时一定回写成功；
 *       现在直接查 {@code F2F_REFUND}，并且最终由
 *       {@code UK_F2F_REFUND_IDEM}（原订单号 + #WHOLE# + PAGE_MANUAL）兜底。</li>
 *   <li><b>退款结果不再靠 {@code retCode} 字符串判断。</b>{@link RefundOutcome}
 *       显式区分「被拒绝」「已存在」「已受理」，运营端能看出是重复点击还是真的拒绝。</li>
 * </ul>
 *
 * <p>不带 {@code @Transactional}：{@link F2fRefundService#refund} 内有支付中心调用。</p>
 */
@Service
public class F2fPageRefundService {

    private static final Logger log = LoggerFactory.getLogger(F2fPageRefundService.class);

    /** 允许人工退款的状态白名单。 */
    private static final List<String> REFUNDABLE = F2fOrderStatus.REFUNDABLE;

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final F2fRefundMapper refundMapper;

    private final F2fRefundService refundService;

    public F2fPageRefundService(F2fOrderMapper orderMapper,
                               F2fPaymentMapper paymentMapper,
                               F2fRefundMapper refundMapper,
                               F2fRefundService refundService) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.refundMapper = refundMapper;
        this.refundService = refundService;
    }

    /**
     * 整单人工退款。金额取订单总额，不接受页面输入。
     *
     * <p><b>四条对外表现逐字对齐旧 {@code FacePayOrderPageController.requestRefund}</b>
     * （2026-09-11 双跑对比后返工）：</p>
     * <ul>
     *   <li>成功时 {@code data} 是 {@code {retCode:"0000", retMsg:"成功"}} —— 旧实现把
     *       {@code tvmOrderPreService.requestRefund} 的原始 JSON 直接 {@code ResultMapper.ok}
     *       出去，运营后台前端按 {@code data.retCode} 判成功。NEVER 改成业务字段对象，
     *       否则前端拿到 {@code undefined} 会判失败；{@code refundNo} 等只进日志。</li>
     *   <li>状态不可退的文案是「仅支付成功的订单可以退款」，<b>不带内部状态名</b>。</li>
     *   <li>已退过的文案是「该订单已发起退款，退款单号：xxx」。</li>
     *   <li><b>查重仍排在状态校验之前</b>：退款不再推进 {@code ORDER_STATUS}（ADR-D88），
     *       第二次调用时订单仍是「支付成功」，两种顺序都能落到查重分支；
     *       但顺序保持不变才能让「已发起退款」这条文案优先于状态文案，与旧实现一致。</li>
     * </ul>
     *
     * <h2>四道闸门，缺一不可</h2>
     * <ol>
     *   <li><b>同来源查重</b>（{@link #findPageRefund}）：本页面已退过就直接答已有单号，
     *       最终由 {@code UK_F2F_REFUND_IDEM} 兜底。</li>
     *   <li><b>跨来源在途拦截</b>（{@code countUnsettledRefunds}）：唯一索引只挡<b>同来源</b>，
     *       设备退款 / 批量退款 / APP 退款各是独立的一行。原实现靠「订单已是 {@code REFUNDING}
     *       就不在 {@code REFUNDABLE} 里」顺带挡住了跨来源重复退，主状态不再变化后
     *       <b>那道防线消失了</b>，MUST 由这一步接替，否则可能在一笔「钱可能已退」的退款之上再退一次。
     *       {@code INIT} / {@code PROCESSING} / {@code MANUAL} 都算未收口 ——
     *       {@code MANUAL} 尤其 NEVER 放行，它的语义是「退没退未知，等人工查」。</li>
     *   <li><b>主状态可退</b>：{@code PAID} / {@code FULFILLED} / {@code FULFILL_FAILED}。</li>
     *   <li><b>可退金额</b>：{@code ORDER_AMOUNT - REFUND_AMOUNT}，退的是<b>剩余金额</b>而非订单总额。
     *       原实现恒退总额，在已有部分退成功的订单上会超额退款；已退满则直接拒绝。</li>
     * </ol>
     *
     * @param operatorId 操作员，可空；落到 {@code F2F_REFUND.OPERATOR_ID}
     */
    public ResultVO<Map<String, Object>> refundWholeOrder(String orderNo, String reason, String operatorId) {
        return doRefund(orderNo, null, reason, operatorId);
    }

    /**
     * 按<b>指定金额</b>人工退款，对齐旧模块 {@code /page/app/orders/{orderNo}/refund}
     * （{@code AppOrderService.refundByAmount}）。四道闸门与
     * {@link #refundWholeOrder} <b>完全共用</b>，唯一差别是金额来自入参。
     *
     * <h2>与旧实现的两处差异（都是有意的）</h2>
     * <ol>
     *   <li><b>旧实现没有任何幂等</b>（原注释原文「同一订单连调两次会退两次」），只有可退余额一道闸门。
     *       本实现保留同来源查重（{@code UK_F2F_REFUND_IDEM} 的
     *       {@code ORIG_ORDER_NO + #WHOLE# + PAGE_MANUAL}），因此
     *       <b>同一订单第二次调用会被判「已发起退款」而拒绝，即使金额不同</b>。
     *       这不是遗漏：唯一索引挡住的正是「同一页面对同一单退两次」这类资损，
     *       而放开它要改索引形状（把第二段换成本次请求唯一键），属独立决策。
     *       <b>NEVER 为了「支持多次部分退」偷偷去掉这道查重</b> —— 那等于把旧实现的资损缺陷搬过来。
     *       确有分次退款需求时 MUST 先定「按什么键幂等」，再改索引 + 迁移脚本。</li>
     *   <li><b>金额上界按剩余可退金额校验</b>（{@code ORDER_AMOUNT - REFUND_AMOUNT}），
     *       超额直接拒绝；旧实现同样有这道闸门，此处逐字保留。</li>
     * </ol>
     *
     * @param refundAmount 本次退款金额，<b>单位分</b>，MUST 大于 0 且不大于剩余可退金额
     */
    public ResultVO<Map<String, Object>> refundByAmount(String orderNo, Long refundAmount,
                                                        String reason, String operatorId) {
        if (refundAmount == null || refundAmount <= 0) {
            return ResultMapper.error("退款金额必须大于 0");
        }
        return doRefund(orderNo, refundAmount, reason, operatorId);
    }

    /**
     * 两个运营端退款入口的公共实现。
     *
     * @param requestedAmount 传 null 即「退剩余可退金额」（整单入口），
     *                        非 null 即按指定金额退（部分退入口）
     */
    private ResultVO<Map<String, Object>> doRefund(String orderNo, Long requestedAmount,
                                                   String reason, String operatorId) {
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return ResultMapper.error("未找到对应的当面付订单");
        }
        F2fRefund existing = findPageRefund(orderNo);
        if (existing != null) {
            return ResultMapper.error("该订单已发起退款，退款单号：" + existing.getRefundNo());
        }
        int unsettled = orderMapper.countUnsettledRefunds(orderNo);
        if (unsettled > 0) {
            log.warn("运营端退款 该订单尚有未收口的退款单，拒绝再退, orderNo={}, unsettled={}", orderNo, unsettled);
            return ResultMapper.error("该订单有退款正在处理中，请等待处理完成后再操作");
        }
        if (!REFUNDABLE.contains(order.getOrderStatus())) {
            log.info("运营端退款 订单状态不允许, orderNo={}, status={}", orderNo, order.getOrderStatus());
            return ResultMapper.error("仅支付成功的订单可以退款");
        }
        if (order.getOrderAmount() == null || order.getOrderAmount() <= 0) {
            return ResultMapper.error("订单金额无效，不能退款");
        }
        long refunded = order.getRefundAmount() == null ? 0L : order.getRefundAmount();
        long refundable = order.getOrderAmount() - refunded;
        if (refundable <= 0) {
            log.info("运营端退款 已退满，无可退金额, orderNo={}, orderAmount={}, refundAmount={}",
                    orderNo, order.getOrderAmount(), refunded);
            return ResultMapper.error("该订单已全额退款，无可退金额");
        }
        long amount = requestedAmount == null ? refundable : requestedAmount;
        if (amount > refundable) {
            log.info("运营端退款 金额超出可退, orderNo={}, requested={}, refundable={}",
                    orderNo, amount, refundable);
            return ResultMapper.error("退款金额不能大于可退金额");
        }

        RefundCommand command = new RefundCommand(orderNo, null, F2fRefundService.SOURCE_PAGE_MANUAL,
                amount, null, reason, null, operatorId,
                order.getTransType(), null, payCenterOrderNoOf(orderNo));
        RefundOutcome outcome = refundService.refund(command);
        if (outcome.isRejected()) {
            log.warn("运营端退款被拒绝, orderNo={}, reason={}", orderNo, outcome.failureReason());
            return ResultMapper.error(outcome.failureReason());
        }
        log.info("运营端退款已受理, orderNo={}, refundNo={}, refundAmount={}, orderAmount={}, "
                        + "alreadyRefunded={}, refundStatus={}, operatorId={}, alreadyExisted={}",
                orderNo, outcome.refundNo(), amount, order.getOrderAmount(), refunded,
                outcome.refundStatus(), operatorId, outcome.alreadyExisted());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("retCode", "0000");
        data.put("retMsg", "成功");
        return ResultMapper.ok(data);
    }

    /** 找该订单已有的运营端退款单；其它来源（设备退款、批量退款）不算重复。 */
    private F2fRefund findPageRefund(String orderNo) {
        List<F2fRefund> refunds = refundMapper.selectByOrigOrderNo(orderNo);
        if (refunds == null) {
            return null;
        }
        for (F2fRefund refund : refunds) {
            if (F2fRefundService.SOURCE_PAGE_MANUAL.equals(refund.getRefundSource())) {
                return refund;
            }
        }
        return null;
    }

    private String payCenterOrderNoOf(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null ? null : last.getPayCenterOrderNo();
    }
}
