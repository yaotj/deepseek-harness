package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.ticket.enums.AdviceOptEnum;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link AdviceOptEnum} 的契约钉子。 */
class AdviceOptEnumTest {

    @Test
    void codesAreFrozenExternalContract() {
        assertEquals("000", AdviceOptEnum.NONE.getCode());
        assertEquals("005", AdviceOptEnum.FREE_UPDATE.getCode());
        assertEquals("006", AdviceOptEnum.PAID_UPDATE.getCode());
        assertEquals("018", AdviceOptEnum.SUPPLEMENT_ENTRY.getCode());
        assertEquals("020", AdviceOptEnum.FREE_UPDATE_020.getCode());
        assertEquals(5, AdviceOptEnum.values().length, "新增取值 MUST 同时确认 isSupplementExit 的归属");
    }

    /** 厂家字典 {@code CardAdviceOpt} 的 {@code FREE_UPDATE} 是 020、{@code FREE_IN_20} 是 005， 与本枚举的 Java 常量名正好错位。 */
    @Test
    void vendorNameCollisionNeverSilentlySwapsCodes() {
        assertEquals("005", AdviceOptEnum.FREE_UPDATE.getCode(), "005 是厂家的 FREE_IN_20，NEVER 让它指向 020");
        assertEquals("020", AdviceOptEnum.FREE_UPDATE_020.getCode(), "020 是厂家的 FREE_UPDATE");
        assertFalse(AdviceOptEnum.FREE_UPDATE.matches("020"));
        assertFalse(AdviceOptEnum.FREE_UPDATE_020.matches("005"));
    }

    /** 方向两组互斥且不重叠：进站组 {@code 018}、出站组 {@code 005 / 006 / 020}。 */
    @Test
    void entryAndExitDirectionsAreDisjoint() {
        assertTrue(AdviceOptEnum.SUPPLEMENT_ENTRY.isSupplementEntry(), "018 是补进站");
        assertFalse(AdviceOptEnum.FREE_UPDATE_020.isSupplementEntry(),
                "020 是「没有时间窗限制的 005」，MUST 走补出站方向");
        assertFalse(AdviceOptEnum.FREE_UPDATE.isSupplementEntry());
        assertFalse(AdviceOptEnum.PAID_UPDATE.isSupplementEntry());
        assertFalse(AdviceOptEnum.NONE.isSupplementEntry());
        for (AdviceOptEnum opt : AdviceOptEnum.values()) {
            assertFalse(opt.isSupplementEntry() && opt.isSupplementExit(),
                    "两组方向 MUST 互斥: " + opt.getCode());
        }
    }

    @Test
    void supplementExitCodesDecideWhetherOrchestratorSkipsCharging() {
        assertEquals(Set.of("005", "006", "020"), AdviceOptEnum.SUPPLEMENT_EXIT_CODES);
        assertTrue(AdviceOptEnum.FREE_UPDATE.isSupplementExit());
        assertTrue(AdviceOptEnum.PAID_UPDATE.isSupplementExit());
        assertTrue(AdviceOptEnum.FREE_UPDATE_020.isSupplementExit(),
                "020 是免费补出站，本笔行程已由 BOM 收口，MUST 跳过出站扣费");
        assertFalse(AdviceOptEnum.SUPPLEMENT_ENTRY.isSupplementExit(), "018 补进站 MUST NOT 跳过扣费");
        assertFalse(AdviceOptEnum.NONE.isSupplementExit());
    }

    @Test
    void unknownCodeParsesToNullAndNeverThrows() {
        assertNull(AdviceOptEnum.fromCode("999"));
        assertNull(AdviceOptEnum.fromCode(null));
        assertNull(AdviceOptEnum.fromCode(""));
        assertFalse(AdviceOptEnum.SUPPLEMENT_EXIT_CODES.contains("999"),
                "未知取值 MUST 落到 shouldPay 的「照常扣费」分支");
    }

    @Test
    void asSingletonListMatchesIf5a01ResponseShape() {
        assertEquals(java.util.List.of("005"), AdviceOptEnum.FREE_UPDATE.asSingletonList());
        assertTrue(AdviceOptEnum.PAID_UPDATE.matches("006"));
        assertFalse(AdviceOptEnum.PAID_UPDATE.matches("005"));
    }
}
