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

/** 过闸扣费订单的落库与状态更新 writer，保证事务生效。 */
@Component
public class GateTxnPayWriter {

    private static final Logger log = LoggerFactory.getLogger(GateTxnPayWriter.class);

    private final GateTxnPayMapper gateTxnPayMapper;
    private final MetroTransferPushTaskMapper metroTransferPushTaskMapper;

    public GateTxnPayWriter(GateTxnPayMapper gateTxnPayMapper, MetroTransferPushTaskMapper metroTransferPushTaskMapper) {
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.metroTransferPushTaskMapper = metroTransferPushTaskMapper;
    }

    /** 插入订单，通过唯一索引保证幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public GateTxnPay insertOrder(GateTxnPay order) {
        try {
            gateTxnPayMapper.insert(order);
        } catch (Exception e) {
            if (!isDuplicateKeyViolation(e)) throw e;
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

    /** 推进待支付订单状态：INIT / RETRY -> PROCESSING / RETRY。 */
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

    /** 支付结果回调驱动的终态收敛，返回实际影响行数。 */
    @Transactional(rollbackFor = Exception.class)
    public int convergeDebitStatus(String orderNo, String txnDate, String debitStatus, String remark) {
        return gateTxnPayMapper.convergeDebitStatus(orderNo, txnDate, debitStatus, remark);
    }

    /** 在线补款支付成功后把原订单收敛为 SUCCESS，返回实际影响行数。 */
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

    /** 离线码金额重算成功后回写金额与优惠快照，返回实际影响行数。 */
    @Transactional(rollbackFor = Exception.class)
    public int applyOfflineFareRecalculated(GateTxnPay order) {
        int updated = gateTxnPayMapper.updateOfflineFareRecalculated(order);
        if (updated == 0) {
            log.warn("离线码金额重算结果未写入（订单已不在待重算态），跳过后续扣款, orderNo={}", order.getOrderNo());
        }
        return updated;
    }

    /** 离线码金额重算再次失败：只记原因，保持待重算态等下一轮补偿，返回实际影响行数。 */
    @Transactional(rollbackFor = Exception.class)
    public int markOfflineFarePending(String orderNo, String txnDate, String reason) {
        int updated = gateTxnPayMapper.updateOfflineFarePendingMsg(orderNo, txnDate, reason);
        if (updated == 0) {
            log.warn("离线码重算失败原因未留痕（订单已不在待重算态）, orderNo={}", orderNo);
        }
        return updated;
    }

    /** 判断异常链上是否存在 {@link DuplicateKeyException}，即唯一约束冲突。 */
    private boolean isDuplicateKeyViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }
}
