package com.chinasofti.huateng.gatetxnpay.writer;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
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

    public GateTxnPayWriter(GateTxnPayMapper gateTxnPayMapper) {
        this.gateTxnPayMapper = gateTxnPayMapper;
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
        } catch (DuplicateKeyException e) {
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

    @Transactional(rollbackFor = Exception.class)
    public void updateOrderStatus(GateTxnPay order, String status, String reason) {
        int updated = gateTxnPayMapper.updateStatusIfProcessing(
                order.getOrderNo(), order.getTxnDate(), status, reason);
        if (updated == 0) {
            log.warn("订单状态未变更，忽略重复或非法状态回调, orderNo={}, currentStatus={}, targetStatus={}",
                    order.getOrderNo(), status, reason);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public GateTxnPay insertOrderAndUpdateStatus(GateTxnPay order, String status, String reason) {
        GateTxnPay saved = insertOrder(order);
        gateTxnPayMapper.updateStatus(saved.getOrderNo(), saved.getTxnDate(), status, reason);
        return saved;
    }
}
