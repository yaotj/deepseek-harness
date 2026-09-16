package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.ticket.enums.AdviceOptEnum;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住 2026-09-14 CardDataHandler 审查里五条**会静默回退**的修复。
 *
 * <p>每个用例都对应一个具体审查项；改动 {@link SupplementStateRules} 时若有用例变红，
 * <b>先确认不是把已修的缺陷改回去了</b>，再谈改用例。</p>
 */
class SupplementStateRulesTest {

    private static final String PAID_AREA = "01";
    private static final String FREE_AREA = "00";

    private SupplementStateRules rules;

    @BeforeEach
    void setUp() {
        rules = newRules("03", "FFFF");
    }

    private SupplementStateRules newRules(String defaultCodeStatus, String defaultLastTxnStation) {
        SupplementStateRules instance = new SupplementStateRules();
        ReflectionTestUtils.setField(instance, "defaultCodeStatus", defaultCodeStatus);
        ReflectionTestUtils.setField(instance, "defaultLastTxnStation", defaultLastTxnStation);
        return instance;
    }

    @Test
    @DisplayName("C001：ticket.default-code-status 不在枚举里时启动即失败，NEVER 静默兜底")
    void invalidDefaultCodeStatusFailsFast() {
        SupplementStateRules broken = newRules("ZZ", "FFFF");
        IllegalStateException error = assertThrows(IllegalStateException.class, broken::validateConfiguredDefaults);
        assertTrue(error.getMessage().contains("ZZ"), "异常信息里 MUST 带上出错的配置值");

        rules.validateConfiguredDefaults();
        assertEquals(QRCodeStatusEnum.SJT_ISSUE, rules.resolveCodeStatus(null));
    }

    @Test
    @DisplayName("M003：列为空按新卡兜底，列有值但未登记 MUST 解析成 null 由调用方拒绝")
    void dirtyCodeStatusIsNotPromotedToNewCard() {
        rules.validateConfiguredDefaults();

        assertEquals(QRCodeStatusEnum.SJT_ISSUE, rules.resolveCodeStatus(null));
        assertEquals(QRCodeStatusEnum.SJT_ISSUE, rules.resolveCodeStatus(""));
        assertEquals(QRCodeStatusEnum.ENTRY, rules.resolveCodeStatus("04"));
        assertNull(rules.resolveCodeStatus("ZZ"), "未登记状态 NEVER 提升成 03 新卡");
    }

    @Test
    @DisplayName("X001：018 补进站对 03 新卡 MUST 同时要求付费区，与建议侧口径一致")
    void supplementEntryOnNewCardRequiresPaidArea() {
        rules.validateConfiguredDefaults();

        assertTrue(rules.isUpdateAllowed(QRCodeStatusEnum.SJT_ISSUE,
                AdviceOptEnum.SUPPLEMENT_ENTRY.getCode(), PAID_AREA, null));
        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.SJT_ISSUE,
                AdviceOptEnum.SUPPLEMENT_ENTRY.getCode(), FREE_AREA, null),
                "非付费区 + 018 是绕过报价路径的口子，MUST 拒绝");

        // 建议侧对同一组合 NEVER 给 018：非付费区的新卡只可能是「刷卡没进成」，给的是 020 免费进闸更新。
        assertEquals(AdviceOptEnum.FREE_UPDATE_020.asSingletonList(),
                rules.resolveAdviceOpt(QRCodeStatusEnum.SJT_ISSUE, "0101", "0101", FREE_AREA, null, "C1"));
    }

    @Test
    @DisplayName("C002：006 付费更新只在开环 / 10 且非付费区放行，其余一律拒绝")
    void paidUpdateWhitelistHasNoUnreachableBranch() {
        rules.validateConfiguredDefaults();
        String paid = AdviceOptEnum.PAID_UPDATE.getCode();

        assertTrue(rules.isUpdateAllowed(QRCodeStatusEnum.ENTRY, paid, FREE_AREA, null));
        assertTrue(rules.isUpdateAllowed(QRCodeStatusEnum.SELF_SERVICE_ENTRY, paid, FREE_AREA, null));
        assertTrue(rules.isUpdateAllowed(QRCodeStatusEnum.UPDATE_ENTRY, paid, FREE_AREA, null));
        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.ENTRY, paid, PAID_AREA, null));
        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.EXIT, paid, FREE_AREA, null));
        assertFalse(rules.isUpdateAllowed(null, paid, FREE_AREA, null), "状态解析不出时 MUST 拒绝");
    }

    @Test
    @DisplayName("M007：进站站未知时 NEVER 建议 006，否则 IF5A-03 必然查不到票价")
    void neverAdvisePaidUpdateWhenEntryStationUnknown() {
        rules.validateConfiguredDefaults();
        String staleGateInTime = LocalDateTime.now().minusHours(2)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        assertEquals(AdviceOptEnum.NONE.asSingletonList(),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "FFFF", "0102", FREE_AREA, staleGateInTime, "C1"),
                "进站站未知 ⇒ 报不出价 ⇒ 不给 006");
        assertEquals(AdviceOptEnum.PAID_UPDATE.asSingletonList(),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "0101", "0102", FREE_AREA, staleGateInTime, "C1"));
    }

    @Test
    @DisplayName("020 在非付费区对任何已登记状态放行（含已出站与新卡）；005 / 006 仍限已进站未出站")
    void freeUpdate020AllowsEveryRegisteredStatusInFreeArea() {
        rules.validateConfiguredDefaults();
        String free005 = AdviceOptEnum.FREE_UPDATE.getCode();
        String paid006 = AdviceOptEnum.PAID_UPDATE.getCode();
        String free020 = AdviceOptEnum.FREE_UPDATE_020.getCode();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String withinWindow = LocalDateTime.now().minusMinutes(5).format(formatter);
        String overWindow = LocalDateTime.now().minusMinutes(90).format(formatter);

        for (QRCodeStatusEnum status : QRCodeStatusEnum.values()) {
            assertTrue(rules.isUpdateAllowed(status, free020, FREE_AREA, overWindow),
                    "020 在非付费区 MUST 放行已登记状态: " + status.getCode() + "(" + status.getDesc() + ")");
        }

        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.ENTRY, free005, FREE_AREA, overWindow),
                "005 MUST 保留 20 分钟上限，NEVER 跟着 020 一起放开");
        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.EXIT, free005, FREE_AREA, withinWindow),
                "005 以「已进站未出站」为前提，NEVER 放宽到闭环态");
        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.SJT_ISSUE, free005, FREE_AREA, withinWindow),
                "005 NEVER 放宽到新卡");
        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.EXIT, paid006, FREE_AREA, withinWindow),
                "006 要按进站站报价，同样 NEVER 放宽到闭环态");

        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.ENTRY, free020, PAID_AREA, withinWindow),
                "付费区仍拒：020 放宽的是状态与时间，不是区域");
        assertFalse(rules.isUpdateAllowed(null, free020, FREE_AREA, withinWindow),
                "状态是脏值（解析不出枚举）时 MUST 拒绝 —— 这是 020 剩下的唯一状态闸口");
    }

    @Test
    @DisplayName("建议侧：非付费区 + 卡上无未完成行程 → 020；开环仍走 005 / 006，付费区一律不给 020")
    void adviseFreeEntryUpdateWhenNoOpenTripInFreeArea() {
        rules.validateConfiguredDefaults();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String withinWindow = LocalDateTime.now().minusMinutes(5).format(formatter);
        String overWindow = LocalDateTime.now().minusMinutes(90).format(formatter);
        List<String> advice020 = AdviceOptEnum.FREE_UPDATE_020.asSingletonList();

        for (QRCodeStatusEnum status : List.of(QRCodeStatusEnum.EXIT, QRCodeStatusEnum.SJT_ISSUE,
                QRCodeStatusEnum.UPDATE_FREE, QRCodeStatusEnum.UPDATE_PAY)) {
            assertEquals(advice020,
                    rules.resolveAdviceOpt(status, "0101", "0102", FREE_AREA, withinWindow, "C1"),
                    "人在闸外 + 卡上没有未闭合进站 ⇒ 就是「刷卡未进站成功」，MUST 建议 020: " + status.getCode());
            assertNotEquals(advice020,
                    rules.resolveAdviceOpt(status, "0101", "0102", PAID_AREA, withinWindow, "C1"),
                    "付费区是补进站 018 的地盘，NEVER 给 020: " + status.getCode());
        }

        assertEquals(AdviceOptEnum.FREE_UPDATE.asSingletonList(),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "0101", "0102", FREE_AREA, withinWindow, "C1"),
                "开环窗内仍是 005 补出站，NEVER 换成 020 —— 那会覆盖原进站基准");
        assertEquals(AdviceOptEnum.PAID_UPDATE.asSingletonList(),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "0101", "0102", FREE_AREA, overWindow, "C1"),
                "开环超窗仍是 006 付费补出站");

        for (QRCodeStatusEnum status : QRCodeStatusEnum.values()) {
            if (advice020.equals(rules.resolveAdviceOpt(status, "0101", "0102", FREE_AREA, withinWindow, "C1"))) {
                assertTrue(rules.isUpdateAllowed(status, AdviceOptEnum.FREE_UPDATE_020.getCode(),
                        FREE_AREA, withinWindow),
                        "建议侧给出的 020 MUST 能过执行侧白名单，否则是「建议了又拒掉」: " + status.getCode());
            }
        }
    }

    @Test
    @DisplayName("免费更新时间窗同时判上下界：设备时钟超前的负差值 MUST 算超窗")
    void freeWindowChecksBothBounds() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        assertTrue(rules.isWithinFreeWindow(LocalDateTime.now().minusMinutes(5).format(formatter)));
        assertFalse(rules.isWithinFreeWindow(LocalDateTime.now().minusMinutes(25).format(formatter)));
        assertFalse(rules.isWithinFreeWindow(LocalDateTime.now().plusMinutes(5).format(formatter)),
                "设备时钟超前时差值为负，只判上界会把 005 无限期放行");
        assertFalse(rules.isWithinFreeWindow("2026"), "长度不足 14 位直接算超窗");
        assertFalse(rules.isWithinFreeWindow(null));
    }
}
