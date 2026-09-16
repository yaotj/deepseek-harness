package com.chinasofti.huateng.paysign.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link PayTxnViews} 的护栏（2026-09-16，ADR-D110 续清单第 5 项）。
 *
 * <p><b>这个类的价值不在「转换对不对」，而在「有没有漏字段」。</b>
 * `PayTxnDetailDTO` 有 26 个字段，收口前那 26 行逐字段拷贝写在
 * {@code PaySignServiceImpl.queryPayTxnBatch} 里且**零覆盖** —— 漏一个 setter 编译通过、
 * 单测通过，只是调用方拿到的那一列恒为 {@code null}。
 *
 * <p>因此主用例用**反射**做：把实体的每个可写字段都填上非空值，转换后遍历 DTO 的**全部 getter**，
 * 任何一个为 {@code null} 就报出字段名。**新增 DTO 字段但忘了在 `toDto` 里搬，这条立刻变红。**
 * <b>NEVER 把它改成逐字段 assertEquals</b> —— 那种写法本身也会漏，等于用同一个缺陷守自己。
 */
class PayTxnViewsTest {

    @Test
    void everyDtoFieldIsPopulated() throws Exception {
        PayTxnDetail entity = fullyPopulatedEntity();

        PayTxnDetailDTO dto = PayTxnViews.toDto(entity);

        List<String> missing = new ArrayList<>();
        for (Method getter : PayTxnDetailDTO.class.getMethods()) {
            if (!isGetter(getter)) {
                continue;
            }
            if (getter.invoke(dto) == null) {
                missing.add(getter.getName());
            }
        }
        assertTrue(missing.isEmpty(),
                "PayTxnViews.toDto 漏搬了这些字段（新增字段 MUST 同步加进 toDto）：" + missing);
    }

    /** 抽查几个语义敏感的字段，确认不是「填上了但填错了」。 */
    @Test
    void keyFieldsMapToTheirCounterparts() {
        PayTxnDetail entity = fullyPopulatedEntity();

        PayTxnDetailDTO dto = PayTxnViews.toDto(entity);

        assertEquals(entity.getOrderNo(), dto.getOrderNo());
        assertEquals(entity.getMerchantOrderNo(), dto.getMerchantOrderNo());
        assertEquals(entity.getPayStatus(), dto.getPayStatus());
        assertEquals(entity.getDebitRequestResult(), dto.getDebitRequestResult());
        assertEquals(entity.getRefundAmount(), dto.getRefundAmount());
    }

    @Test
    void nullAndEmptyInputYieldEmptyListNotNull() {
        assertTrue(PayTxnViews.toDtoList(null).isEmpty());
        assertTrue(PayTxnViews.toDtoList(new ArrayList<>()).isEmpty());
    }

    @Test
    void listConversionKeepsOrder() {
        PayTxnDetail first = fullyPopulatedEntity();
        first.setOrderNo("GT-FIRST");
        PayTxnDetail second = fullyPopulatedEntity();
        second.setOrderNo("GT-SECOND");

        List<PayTxnDetailDTO> dtoList = PayTxnViews.toDtoList(List.of(first, second));

        assertEquals(2, dtoList.size());
        assertEquals("GT-FIRST", dtoList.get(0).getOrderNo());
        assertEquals("GT-SECOND", dtoList.get(1).getOrderNo());
    }

    /** 用反射把实体所有 setter 都填上非空值 —— 这样「DTO 某字段为 null」只可能是漏搬。 */
    private PayTxnDetail fullyPopulatedEntity() {
        PayTxnDetail entity = new PayTxnDetail();
        int seq = 1;
        for (Method setter : PayTxnDetail.class.getMethods()) {
            if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1) {
                continue;
            }
            Class<?> type = setter.getParameterTypes()[0];
            try {
                if (type == String.class) {
                    setter.invoke(entity, setter.getName().substring(3) + "-" + seq);
                } else if (type == Integer.class || type == int.class) {
                    setter.invoke(entity, seq);
                } else if (type == Long.class || type == long.class) {
                    setter.invoke(entity, (long) seq);
                } else if (type == LocalDateTime.class) {
                    setter.invoke(entity, LocalDateTime.now().minusMinutes(seq));
                } else {
                    continue;
                }
                seq++;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("填充实体失败: " + setter.getName(), e);
            }
        }
        return entity;
    }

    private boolean isGetter(Method method) {
        if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
            return false;
        }
        String name = method.getName();
        return (name.startsWith("get") && !"getClass".equals(name)) || name.startsWith("is");
    }
}
