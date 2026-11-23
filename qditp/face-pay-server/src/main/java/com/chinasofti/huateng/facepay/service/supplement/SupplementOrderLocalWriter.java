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
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
public class SupplementOrderLocalWriter {

    private static final Logger log = LoggerFactory.getLogger(SupplementOrderLocalWriter.class);

    private final SupplementOrderMapper supplementOrderMapper;

    public SupplementOrderLocalWriter(SupplementOrderMapper supplementOrderMapper) {
        this.supplementOrderMapper = supplementOrderMapper;
    }

    /**
     * 单号撞库换号重试上限。只在「同一张卡同一毫秒提交」这种物理碰撞下才会触发第二、三轮，
     * 正常路径一次插入即成功。
     */
    private static final int ORDER_NO_RETRY_LIMIT = 3;

    @Transactional
    public String persist(SupplementOrder order,
                          List<String> orderNos,
                          Map<String, GateTxnPay> orderIndex) {
        insertWithOrderNoRetry(order);
        String orderNo = order.getOrderNo();

        LocalDateTime now = LocalDateTime.now();
        for (String origOrderNo : orderNos) {
            GateTxnPay gateTxnPay = orderIndex.get(origOrderNo);
            SupplementOrderItem item = new SupplementOrderItem();
            item.setOrderNo(orderNo);
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

        log.info("补款单本地落单完成, orderNo={}, itemCount={}", orderNo, orderNos.size());
        return orderNo;
    }

    /**
     * 主表插入。撞唯一索引**不视为业务重复**（2026-11-20 裁决）：补款是抢占式，同一批欠费允许
     * 生成多张补款单、谁先付款谁结清，建单侧 NEVER 做业务防重。
     * <p>
     * 这里能撞上的唯一键只有 {@code UK_SUPPLEMENT_ORDER_NO}（ORDER_NO = 前缀 + 毫秒时间戳 + 卡号后 6 位），
     * 即「同一张卡在同一毫秒内提交了两次」的物理碰撞。这种碰撞 MUST 换号重试：
     * 把请求打回「补款单已存在，请勿重复提交」等于凭空拒绝一次合法下单。
     */
    private void insertWithOrderNoRetry(SupplementOrder order) {
        for (int attempt = 1; ; attempt++) {
            try {
                supplementOrderMapper.insert(order);
                return;
            } catch (RuntimeException e) {
                if (!F2fDuplicateKey.isConflict(e)) {
                    throw e;
                }
                if (attempt >= ORDER_NO_RETRY_LIMIT) {
                    log.error("补款主表单号连续撞库，放弃本次下单, orderNo={}, attempts={}",
                            order.getOrderNo(), attempt, e);
                    throw e;
                }
                String retried = order.getOrderNo() + (1000 + ThreadLocalRandom.current().nextInt(9000));
                log.warn("补款主表单号撞库，换号重试, from={}, to={}", order.getOrderNo(), retried);
                order.setOrderNo(retried);
            }
        }
    }
}
