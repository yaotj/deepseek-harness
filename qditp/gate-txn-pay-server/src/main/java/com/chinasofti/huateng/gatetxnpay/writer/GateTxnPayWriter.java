package com.chinasofti.huateng.gatetxnpay.writer;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.mapper.MetroTransferPushTaskMapper;
import com.chinasofti.huateng.gatetxnpay.entity.MetroTransferPushTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 过闸扣费订单的落库与状态更新 writer，保证事务生效。
 *
 * <p>使用数据库唯一索引保证幂等，无需分布式锁。</p>
 *
 * <p>单独拆分为组件，避免 GateTxnPayServiceImpl 内部自调用导致
 * {@code @Transactional} 失效的问题。</p>
 */
@Component
public class GateTxnPayWriter {

    private static final Logger log = LoggerFactory.getLogger(GateTxnPayWriter.class);

    private final GateTxnPayMapper gateTxnPayMapper;
    private final MetroTransferPushTaskMapper metroTransferPushTaskMapper;

    public GateTxnPayWriter(GateTxnPayMapper gateTxnPayMapper, MetroTransferPushTaskMapper metroTransferPushTaskMapper) {
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.metroTransferPushTaskMapper = metroTransferPushTaskMapper;
    }

    /**
     * 插入订单，通过唯一索引保证幂等。
     *
     * <p>并发场景：唯一索引冲突时，查询已有订单返回，避免重复创建。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public GateTxnPay insertOrder(GateTxnPay order) {
        try {
            gateTxnPayMapper.insert(order);
        } catch (Exception e) {
            if (!isDuplicateKeyViolation(e)) throw e;
            // 唯一索引冲突，查询已有订单返回
            GateTxnPay existing = gateTxnPayMapper.selectByBizKey(
                    order.getCardId(), order.getTrxType(), order.getOutTime(),
                    order.getTicketTransSeq(), order.getDeviceId(), order.getTxnDate());
            if (existing == null) {
                throw new IllegalStateException("订单唯一索引冲突但查询为空，cardId=" + order.getCardId(), e);
            }
            log.warn("并发重复订单已存在，返回已有订单, cardId={}, orderNo={}, trxType={}, outTime={}",
                    existing.getCardId(), existing.getOrderNo(),
                    existing.getTrxType(), existing.getOutTime());
            return existing;
        }
        return order;
    }

    /** 订单与公交 outbox 在同一事务中写入，避免订单成功但漏建推送任务。 */
    @Transactional(rollbackFor = Exception.class)
    public GateTxnPay insertOrderWithMetroTransferPushTask(GateTxnPay order, MetroTransferPushTask task) {
        GateTxnPay saved = insertOrder(order);
        if (task != null) {
            task.setOrderNo(saved.getOrderNo());
            createMetroTransferPushTask(task);
        }
        return saved;
    }

    /**
     * 推进待支付订单状态：INIT / RETRY -> PROCESSING / RETRY。
     *
     * <p>已受理（PROCESSING）与终态订单不会被改写，返回 0 行时仅告警不抛异常。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderStatusFromPending(GateTxnPay order, String status, String reason) {
        int updated = gateTxnPayMapper.updateStatusFromPending(
                order.getOrderNo(), order.getTxnDate(), status, reason);
        if (updated == 0) {
            log.warn("订单不处于待支付态(INIT/RETRY)，跳过状态推进, orderNo={}, targetStatus={}, reason={}",
                    order.getOrderNo(), status, reason);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public GateTxnPay insertOrderAndUpdateStatus(GateTxnPay order, String status, String reason) {
        GateTxnPay saved = insertOrder(order);
        gateTxnPayMapper.updateStatus(saved.getOrderNo(), saved.getTxnDate(), status, reason);
        return saved;
    }

    /**
     * 支付结果回调驱动的终态收敛，返回实际影响行数。
     *
     * <p>返回 0 表示订单已是终态或不存在，调用方 MUST 自行区分并落日志，
     * NEVER 当成成功——重复回调与「订单号对不上」在这里是同一个返回值。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int convergeDebitStatus(String orderNo, String txnDate, String debitStatus, String remark) {
        return gateTxnPayMapper.convergeDebitStatus(orderNo, txnDate, debitStatus, remark);
    }

    /**
     * 在线补款支付成功后把原订单收敛为 SUCCESS，返回实际影响行数。
     *
     * <p>与 {@link #convergeDebitStatus} 的差别只有一处但很关键：<b>白名单多一个 {@code FAIL}</b>。
     * 补款下单放行的欠费口径含 FAIL 单，能下单就必须能收敛，否则钱已实收而行程仍挂欠费。
     * 详见 {@code GateTxnPayMapper.convergeDebitStatusForSupplement} 的注释，
     * <b>NEVER 把两个方法合并</b>。</p>
     *
     * <p>返回 0 表示「已被别人收敛」或「状态不在白名单 / 订单不存在」，
     * 调用方 MUST 回查当前状态区分这两类，NEVER 当成成功也 NEVER 一律当失败 ——
     * 前者意味着重复扣款待退款，后者才是需人工核对。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int convergeDebitStatusForSupplement(String orderNo, String txnDate, String remark) {
        return gateTxnPayMapper.convergeDebitStatusForSupplement(orderNo, txnDate, remark);
    }

    @Transactional(rollbackFor = Exception.class)
    public GateTxnPay insertOrderAndUpdateStatusWithMetroTransferPushTask(
            GateTxnPay order, String status, String reason, MetroTransferPushTask task) {
        GateTxnPay saved = insertOrder(order);
        gateTxnPayMapper.updateStatus(saved.getOrderNo(), saved.getTxnDate(), status, reason);
        if (task != null) {
            task.setOrderNo(saved.getOrderNo());
            createMetroTransferPushTask(task);
        }
        return saved;
    }

    /** 以订单号唯一约束保证同一笔过闸交易只产生一个公交推送任务。 */
    @Transactional(rollbackFor = Exception.class)
    public void createMetroTransferPushTask(MetroTransferPushTask task) {
        try {
            metroTransferPushTaskMapper.insertIgnoreDuplicate(task);
        } catch (Exception e) {
            if (!isDuplicateKeyViolation(e)) throw e;
            log.info("公交推送任务已存在，忽略重复创建, orderNo={}", task.getOrderNo());
        }
    }

    /**
     * 离线码金额重算成功后回写金额与优惠快照，返回实际影响行数。
     *
     * <p>返回 0 表示这笔已被别的副本重算或已被人工干预，调用方 <b>MUST</b> 就此终止、
     * <b>NEVER</b> 继续调 pay-sign——否则同一笔会被重复发起扣款。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int applyOfflineFareRecalculated(GateTxnPay order) {
        int updated = gateTxnPayMapper.updateOfflineFareRecalculated(order);
        if (updated == 0) {
            log.warn("离线码金额重算结果未写入（订单已不在待重算态），跳过后续扣款, orderNo={}", order.getOrderNo());
        }
        return updated;
    }

    /**
     * 离线码金额重算再次失败：只记原因，保持待重算态等下一轮补偿，返回实际影响行数。
     *
     * <p>返回 0 表示这笔已不在待重算态（被别的副本推进或已人工干预）。调用方 <b>MUST</b> 记日志，
     * <b>NEVER</b> 把 0 行当成「原因已留痕」—— 改造前本方法返回 void，0 行被静默吞掉，
     * 运维会以为库里能查到失败原因（{@code docs/domain/outbox.md} §二）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int markOfflineFarePending(String orderNo, String txnDate, String reason) {
        int updated = gateTxnPayMapper.updateOfflineFarePendingMsg(orderNo, txnDate, reason);
        if (updated == 0) {
            log.warn("离线码重算失败原因未留痕（订单已不在待重算态）, orderNo={}", orderNo);
        }
        return updated;
    }

    /**
     * 判断异常链上是否存在 {@link DuplicateKeyException}，即唯一约束冲突。
     *
     * <p><b>MUST</b> 逐层遍历 cause，<b>NEVER</b> 直接 {@code catch (DuplicateKeyException)}：
     * {@code MapperAspectToTrace}（{@code resource/micro/web/src/main/java/com/chinasofti/huateng/
     * micro/monitor/trace/MapperAspectToTrace.java:51}）在 {@code management.tracing.enabled=true}
     * 时把 mapper 抛出的任何异常统一包成 {@code RuntimeException}，单层类型判断就捕不到，
     * 本类赖以实现幂等的「唯一索引 + DuplicateKeyException 兜底」会整体失效。
     * 本模块当前未注入该 env（判断走 {@code resource/micro/web/.../web.properties:78} 的默认
     * {@code false}），但那是配置巧合而非代码保证——2026-09-08 已在 account-server 与
     * para-server 上实测到失效后果，此处按防御写法统一收口。</p>
     */
    private boolean isDuplicateKeyViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }
}
