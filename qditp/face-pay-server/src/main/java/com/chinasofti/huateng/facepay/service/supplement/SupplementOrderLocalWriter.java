package com.chinasofti.huateng.facepay.service.supplement;

import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.entity.GateTxnPay;
import com.chinasofti.huateng.facepay.entity.SupplementOrder;
import com.chinasofti.huateng.facepay.entity.SupplementOrderItem;
import com.chinasofti.huateng.facepay.mapper.SupplementOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class SupplementOrderLocalWriter {

    private static final Logger log = LoggerFactory.getLogger(SupplementOrderLocalWriter.class);

    private final SupplementOrderMapper supplementOrderMapper;

    public SupplementOrderLocalWriter(SupplementOrderMapper supplementOrderMapper) {
        this.supplementOrderMapper = supplementOrderMapper;
    }

    public record PersistResult(boolean persisted, boolean rejected, String orderNo) {
        public static PersistResult ok(String orderNo) {
            return new PersistResult(true, false, orderNo);
        }

        public static PersistResult rejected(String orderNo) {
            return new PersistResult(false, true, orderNo);
        }
    }

    /**
     * 本地落单：插入主表 + 明细。
     *
     * <p>无独占设计：同一行程单允许同时挂在多张补款单下，先到先得——谁先支付成功
     * 谁就收敛行程单，后到的重复支付由 settleSuccess 标 FAILED 记「重复支付待退款」。
     * 明细的 ACTIVE_ORIG_ORDER_NO 已废弃（恒写 NULL），UK_SUPPLEMENT_ITEM_ACTIVE
     * 唯一索引因此永不触发，撞键只可能来自主表 ORDER_NO 唯一索引（重试幂等）。</p>
     */
    @Transactional
    public PersistResult persist(SupplementOrder order,
                                 List<String> orderNos,
                                 Map<String, GateTxnPay> orderIndex) {
        try {
            supplementOrderMapper.insert(order);
        } catch (RuntimeException e) {
            if (F2fDuplicateKey.isConflict(e)) {
                log.warn("补款主表撞唯一索引，视为重复下单拒绝, orderNo={}", order.getOrderNo(), e);
                return PersistResult.rejected(order.getOrderNo());
            }
            throw e;
        }

        LocalDateTime now = LocalDateTime.now();
        for (String origOrderNo : orderNos) {
            GateTxnPay gateTxnPay = orderIndex.get(origOrderNo);
            SupplementOrderItem item = new SupplementOrderItem();
            item.setOrderNo(order.getOrderNo());
            item.setOrigOrderNo(origOrderNo);
            item.setOrigTxnDate(gateTxnPay != null ? gateTxnPay.getTxnDate() : null);
            item.setOrigAmount(gateTxnPay != null && gateTxnPay.getTotalAmount() != null
                    ? gateTxnPay.getTotalAmount().longValue() : null);
            item.setSettleStatus("PENDING");
            item.setActiveOrigOrderNo(null);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            supplementOrderMapper.insertItem(item);
        }

        log.info("补款单本地落单完成, orderNo={}, itemCount={}", order.getOrderNo(), orderNos.size());
        return PersistResult.ok(order.getOrderNo());
    }
}
