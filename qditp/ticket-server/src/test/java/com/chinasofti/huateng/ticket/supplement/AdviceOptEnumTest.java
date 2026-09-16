package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.ticket.enums.AdviceOptEnum;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AdviceOptEnum} 的契约钉子。
 *
 * <p>2026-09-14 把原先散在三处的 adviceOpt 字典收口到 model 后补的回归测试。
 * 这些断言不是「测试枚举能不能取值」，而是钉住两条**改坏了会丢钱**的不变量：
 * <ul>
 *   <li>码值是与 BOM / 闸机的对外契约，改一个字符即协议不兼容；</li>
 *   <li>{@code SUPPLEMENT_EXIT_CODES} 决定 {@code gate.GateFarePaymentOrchestrator} 的
 *       {@code shouldPay} 跳不跳扣费，多一个取值即漏扣、少一个即重扣
 *       （2026-09-14 / ADR-D62 前这段判据住在 fep-dev-server，本注释已随迁移更新）。</li>
 * </ul>
 */
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

    /**
     * 厂家字典 {@code CardAdviceOpt} 的 {@code FREE_UPDATE} 是 020、{@code FREE_IN_20} 是 005，
     * 与本枚举的 Java 常量名正好错位。这两行钉住「005 永远是 005」——把 {@code FREE_UPDATE}
     * 改名指向 020 时现有调用点仍能编译、含义却全变，只有本断言会红。
     *
     * <p><b>两者不是同一件事的宽严版本，而是方向相反</b>：005 补出站、020 补进站
     * （用户 2026-09-15 裁决），方向判据见 {@link #entryAndExitDirectionsAreDisjoint}。
     */
    @Test
    void vendorNameCollisionNeverSilentlySwapsCodes() {
        assertEquals("005", AdviceOptEnum.FREE_UPDATE.getCode(), "005 是厂家的 FREE_IN_20，NEVER 让它指向 020");
        assertEquals("020", AdviceOptEnum.FREE_UPDATE_020.getCode(), "020 是厂家的 FREE_UPDATE");
        assertFalse(AdviceOptEnum.FREE_UPDATE.matches("020"));
        assertFalse(AdviceOptEnum.FREE_UPDATE_020.matches("005"));
    }

    /**
     * 方向两组互斥且不重叠：进站组 {@code 018 / 020}、出站组 {@code 005 / 006}。
     * {@code CardDataUpdateHandler.resolveTrxType} 完全靠这两个判据决定 {@code TRX_TYPE}，
     * 任何一个码同时命中两组都会让那段 if 链的结果取决于书写顺序。
     */
    @Test
    void entryAndExitDirectionsAreDisjoint() {
        assertTrue(AdviceOptEnum.SUPPLEMENT_ENTRY.isSupplementEntry(), "018 是补进站");
        assertTrue(AdviceOptEnum.FREE_UPDATE_020.isSupplementEntry(),
                "020 是「刷卡未进站成功」的免费进闸更新，MUST 走补进站方向");
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
        assertEquals(Set.of("005", "006"), AdviceOptEnum.SUPPLEMENT_EXIT_CODES);
        assertTrue(AdviceOptEnum.FREE_UPDATE.isSupplementExit());
        assertTrue(AdviceOptEnum.PAID_UPDATE.isSupplementExit());
        assertFalse(AdviceOptEnum.FREE_UPDATE_020.isSupplementExit(),
                "020 改成补进站后 MUST NOT 跳过扣费 —— 乘客随后要真实出站，跳过即整程免费（资损）");
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
