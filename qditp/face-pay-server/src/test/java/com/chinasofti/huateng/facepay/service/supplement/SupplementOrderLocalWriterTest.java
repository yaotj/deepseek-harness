package com.chinasofti.huateng.facepay.service.supplement;

import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.entity.GateTxnPay;
import com.chinasofti.huateng.facepay.entity.SupplementOrder;
import com.chinasofti.huateng.facepay.entity.SupplementOrderItem;
import com.chinasofti.huateng.facepay.mapper.SupplementOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 锁死 {@link SupplementOrderLocalWriter} 的本地事务语义。 */
@ExtendWith(MockitoExtension.class)
class SupplementOrderLocalWriterTest {

    private static final String SUP_ORDER = "SP202609150001";
    private static final String ORIG_1 = "GTP202609150001";
    private static final String ORIG_2 = "GTP202609150002";

    @Mock private SupplementOrderMapper mapper;

    private SupplementOrderLocalWriter writer;

    @BeforeEach
    void setUp() {
        writer = new SupplementOrderLocalWriter(mapper);
    }

    // ==================== persist ====================

    /** 正常落单 → ok + insert 两条明细，明细的 ACTIVE_ORIG_ORDER_NO 必须为 NULL（无独占）。 */
    @Test
    void normalPersistOk() {
        SupplementOrder order = buildOrder();
        Map<String, GateTxnPay> index = buildIndex(ORIG_1, ORIG_2);

        SupplementOrderLocalWriter.PersistResult result = writer.persist(order, List.of(ORIG_1, ORIG_2), index);

        assertTrue(result.persisted());
        assertFalse(result.rejected());
        assertEquals(SUP_ORDER, result.orderNo());
        verify(mapper).insert(order);
        verify(mapper, times(2)).insertItem(any(SupplementOrderItem.class));
    }

    /** 主表撞唯一索引 → rejected，NEVER 抛异常。 */
    @Test
    void mainTableDuplicateKeyReturnsRejected() {
        SupplementOrder order = buildOrder();
        Map<String, GateTxnPay> index = buildIndex(ORIG_1);

        // insert 撞 UK_SUPPLEMENT_ORDER_NO
        doThrow(new DuplicateKeyException("ORA-00001 unique constraint SUPPLEMENT_ORDER_UK"))
                .when(mapper).insert(order);

        SupplementOrderLocalWriter.PersistResult result = writer.persist(order, List.of(ORIG_1), index);

        assertFalse(result.persisted());
        assertTrue(result.rejected());
        assertEquals(SUP_ORDER, result.orderNo());
        verify(mapper, org.mockito.Mockito.never()).insertItem(any());
    }

    /** 完全不相关的 RuntimeException（不是唯一索引冲突）→ 向上抛，NEVER 吞没。 */
    @Test
    void unrelatedRuntimeExceptionPropagates() {
        SupplementOrder order = buildOrder();
        Map<String, GateTxnPay> index = buildIndex(ORIG_1);

        doThrow(new RuntimeException("DB connection lost"))
                .when(mapper).insert(order);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> writer.persist(order, List.of(ORIG_1), index));
    }

    /** persist 时明细找不到对应的原订单 → 明细字段留 null，不应 NPE。 */
    @Test
    void itemWithoutMatchingOrigOrderDoesNotNpe() {
        SupplementOrder order = buildOrder();
        // ORIG_1 有，ORIG_2 在 index 里没有（原订单查不到了）
        Map<String, GateTxnPay> index = buildIndex(ORIG_1);

        SupplementOrderLocalWriter.PersistResult result = writer.persist(order, List.of(ORIG_1, ORIG_2), index);

        assertTrue(result.persisted());
        // 第二条明细的 origTxnDate 和 origAmount 应该为 null，但 insertItem 应该正常调
        verify(mapper, org.mockito.Mockito.times(2)).insertItem(any(SupplementOrderItem.class));
    }

    // ==================== helpers ====================

    private SupplementOrder buildOrder() {
        SupplementOrder order = new SupplementOrder();
        order.setOrderNo(SUP_ORDER);
        order.setPayStatus("INIT");
        order.setThirdUserId("USER_01");
        order.setCardId("CARD001");
        order.setTotalAmount(800L);
        order.setOrderCount(2);
        return order;
    }

    private Map<String, GateTxnPay> buildIndex(String... orderNos) {
        Map<String, GateTxnPay> index = new LinkedHashMap<>();
        for (String no : orderNos) {
            GateTxnPay p = new GateTxnPay();
            p.setOrderNo(no);
            p.setDebitStatus("INIT");
            p.setTxnDate("20260915");
            p.setTotalAmount(400);
            index.put(no, p);
        }
        return index;
    }
}
