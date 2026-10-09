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

/** 钉住 2026-09-14 CardDataHandler 审查里五条会静默回退的修复。 */
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

        assertEquals(AdviceOptEnum.NONE.asSingletonList(),
                rules.resolveAdviceOpt(QRCodeStatusEnum.SJT_ISSUE, "0101", "0101", FREE_AREA, null, "C1"),
                "新卡在非付费区不给建议：「刷卡未进站成功」这个场景按用户 2026-09-18 裁决不处理");
    }

    @Test
    @DisplayName("M007：进站站未知时 NEVER 建议 006，但仍可给无时间窗的 020")
    void neverAdvisePaidUpdateWhenEntryStationUnknown() {
        rules.validateConfiguredDefaults();
        String staleGateInTime = LocalDateTime.now().minusHours(2)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        assertEquals(AdviceOptEnum.FREE_UPDATE_020.asSingletonList(),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "FFFF", "0102", FREE_AREA, staleGateInTime, "C1"),
                "进站站未知 ⇒ 报不出价 ⇒ 只给 020，NEVER 给 006");
        assertEquals(List.of(AdviceOptEnum.PAID_UPDATE.getCode(), AdviceOptEnum.FREE_UPDATE_020.getCode()),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "0101", "0102", FREE_AREA, staleGateInTime, "C1"),
                "能报价时两个候选都给，006 MUST 排首位（BOM 取第一个当默认）");
    }

    @Test
    @DisplayName("020 = 没有时间窗限制的 005：状态白名单与区域要求与 005 一致，只是不复核 20 分钟窗")
    void freeUpdate020IsFreeUpdateWithoutTimeWindow() {
        rules.validateConfiguredDefaults();
        String free005 = AdviceOptEnum.FREE_UPDATE.getCode();
        String paid006 = AdviceOptEnum.PAID_UPDATE.getCode();
        String free020 = AdviceOptEnum.FREE_UPDATE_020.getCode();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String withinWindow = LocalDateTime.now().minusMinutes(5).format(formatter);
        String overWindow = LocalDateTime.now().minusMinutes(90).format(formatter);

        for (QRCodeStatusEnum status : List.of(QRCodeStatusEnum.ENTRY, QRCodeStatusEnum.SELF_SERVICE_ENTRY,
                QRCodeStatusEnum.UPDATE_ENTRY)) {
            assertTrue(rules.isUpdateAllowed(status, free020, FREE_AREA, overWindow),
                    "020 对「已进站未出站」MUST 放行且不看时间窗: " + status.getCode());
            assertTrue(rules.isUpdateAllowed(status, free020, FREE_AREA, null),
                    "020 连 gateInTime 都不上送也 MUST 放行: " + status.getCode());
        }

        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.ENTRY, free005, FREE_AREA, overWindow),
                "005 MUST 保留 20 分钟上限，这是它与 020 的唯一差别");
        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.EXIT, free020, FREE_AREA, withinWindow),
                "020 与 005 同白名单，闭环态没有未完成行程可补出站，MUST 拒绝");
        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.SJT_ISSUE, free020, FREE_AREA, withinWindow),
                "020 NEVER 放宽到新卡");
        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.EXIT, paid006, FREE_AREA, withinWindow),
                "006 要按进站站报价，同样 NEVER 放宽到闭环态");

        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.ENTRY, free020, PAID_AREA, withinWindow),
                "付费区仍拒：020 放宽的是时间窗，不是区域");
        assertFalse(rules.isUpdateAllowed(null, free020, FREE_AREA, withinWindow),
                "状态是脏值（解析不出枚举）时 MUST 拒绝");
    }

    @Test
    @DisplayName("建议侧：卡上无未完成行程时一律 000（该场景不处理）；开环窗内 005、超窗 [006, 020]")
    void adviseNothingWhenNoOpenTripInFreeArea() {
        rules.validateConfiguredDefaults();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String withinWindow = LocalDateTime.now().minusMinutes(5).format(formatter);
        String overWindow = LocalDateTime.now().minusMinutes(90).format(formatter);
        List<String> advice020 = AdviceOptEnum.FREE_UPDATE_020.asSingletonList();

        for (QRCodeStatusEnum status : List.of(QRCodeStatusEnum.EXIT, QRCodeStatusEnum.SJT_ISSUE,
                QRCodeStatusEnum.UPDATE_FREE, QRCodeStatusEnum.UPDATE_PAY)) {
            assertEquals(AdviceOptEnum.NONE.asSingletonList(),
                    rules.resolveAdviceOpt(status, "0101", "0102", FREE_AREA, withinWindow, "C1"),
                    "卡上没有未闭合进站 ⇒ 补出站无从谈起，MUST 给 000: " + status.getCode());
            assertNotEquals(advice020,
                    rules.resolveAdviceOpt(status, "0101", "0102", PAID_AREA, withinWindow, "C1"),
                    "付费区是补进站 018 的地盘，NEVER 给 020: " + status.getCode());
        }

        assertEquals(AdviceOptEnum.FREE_UPDATE.asSingletonList(),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "0101", "0102", FREE_AREA, withinWindow, "C1"),
                "开环窗内仍是 005，NEVER 换成 020 —— 窗内本来就免费，020 是给超窗用的");
        assertEquals(List.of(AdviceOptEnum.PAID_UPDATE.getCode(), AdviceOptEnum.FREE_UPDATE_020.getCode()),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "0101", "0102", FREE_AREA, overWindow, "C1"),
                "开环超窗给两个候选：006 付费在前 / 020 免费兜底，NEVER 调回 020 在前");

        for (QRCodeStatusEnum status : QRCodeStatusEnum.values()) {
            List<String> advice = rules.resolveAdviceOpt(status, "0101", "0102", FREE_AREA, overWindow, "C1");
            if (advice.contains(AdviceOptEnum.FREE_UPDATE_020.getCode())) {
                assertTrue(rules.isUpdateAllowed(status, AdviceOptEnum.FREE_UPDATE_020.getCode(),
                        FREE_AREA, overWindow),
                        "建议侧给出的 020 MUST 能过执行侧白名单，否则是「建议了又拒掉」: " + status.getCode());
            }
        }
    }

    @Test
    @DisplayName("C002：006 付费更新只在开环 / 10 + 非付费区 + 已确认超窗时放行，其余一律拒绝")
    void paidUpdateWhitelistHasNoUnreachableBranch() {
        rules.validateConfiguredDefaults();
        String paid = AdviceOptEnum.PAID_UPDATE.getCode();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String withinWindow = LocalDateTime.now().minusMinutes(5).format(formatter);
        String overWindow = LocalDateTime.now().minusMinutes(90).format(formatter);

        assertTrue(rules.isUpdateAllowed(QRCodeStatusEnum.ENTRY, paid, FREE_AREA, overWindow));
        assertTrue(rules.isUpdateAllowed(QRCodeStatusEnum.SELF_SERVICE_ENTRY, paid, FREE_AREA, overWindow));
        assertTrue(rules.isUpdateAllowed(QRCodeStatusEnum.UPDATE_ENTRY, paid, FREE_AREA, overWindow));
        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.ENTRY, paid, PAID_AREA, overWindow));
        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.EXIT, paid, FREE_AREA, overWindow));
        assertFalse(rules.isUpdateAllowed(null, paid, FREE_AREA, overWindow), "状态解析不出时 MUST 拒绝");

        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.ENTRY, paid, FREE_AREA, withinWindow),
                "窗内本可免费更新，NEVER 放行付费更新（用户 2026-09-18「免费不扣费」裁决）");
        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.ENTRY, paid, FREE_AREA, null),
                "进站时间缺失时证明不了超窗，MUST 拒绝收费，该走 020 免费更新");
        assertFalse(rules.isUpdateAllowed(QRCodeStatusEnum.ENTRY, paid, FREE_AREA, "2026"),
                "进站时间解析不出时同样 MUST 拒绝收费");
    }

    @Test
    @DisplayName("拒绝归因：状态与区域在前、时间窗在后，状态本来就不允许时 NEVER 报成时间窗原因")
    void rejectionReasonPrefersStateOverFreeWindow() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String withinWindow = LocalDateTime.now().minusMinutes(5).format(formatter);
        String overWindow = LocalDateTime.now().minusMinutes(90).format(formatter);

        assertEquals(SupplementStateRules.UpdateRejection.STATE_NOT_ALLOWED,
                rules.checkUpdate(QRCodeStatusEnum.SJT_ISSUE, AdviceOptEnum.PAID_UPDATE.getCode(),
                        FREE_AREA, null),
                "03 新卡不在 006 白名单，MUST 报状态原因而不是「未确认超窗」");
        assertEquals(SupplementStateRules.UpdateRejection.STATE_NOT_ALLOWED,
                rules.checkUpdate(QRCodeStatusEnum.EXIT, AdviceOptEnum.FREE_UPDATE.getCode(),
                        FREE_AREA, overWindow),
                "闭环不在 005 白名单，MUST 报状态原因而不是「时间窗已过」");

        assertEquals(SupplementStateRules.UpdateRejection.FREE_WINDOW_NOT_EXPIRED,
                rules.checkUpdate(QRCodeStatusEnum.ENTRY, AdviceOptEnum.PAID_UPDATE.getCode(),
                        FREE_AREA, withinWindow));
        assertEquals(SupplementStateRules.UpdateRejection.FREE_WINDOW_EXPIRED,
                rules.checkUpdate(QRCodeStatusEnum.ENTRY, AdviceOptEnum.FREE_UPDATE.getCode(),
                        FREE_AREA, overWindow));
        assertEquals(SupplementStateRules.UpdateRejection.NONE,
                rules.checkUpdate(QRCodeStatusEnum.ENTRY, AdviceOptEnum.PAID_UPDATE.getCode(),
                        FREE_AREA, overWindow));
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

    @Test
    @DisplayName("站点感知（IF5A-01 建议侧）：同站窗内→005 免费；跨站不论窗内窗外→006 付费；站码未知→旧口径")
    void stationAwareAdviceOpt() {
        rules.validateConfiguredDefaults();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String withinWindow = LocalDateTime.now().minusMinutes(5).format(formatter);
        String overWindow = LocalDateTime.now().minusMinutes(90).format(formatter);

        // 同站 + 窗内 ⇒ 免费更新 005
        assertEquals(AdviceOptEnum.FREE_UPDATE.asSingletonList(),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "0101", "0101", FREE_AREA, withinWindow, "C1", "0101"),
                "进站站与 BOM 站码相同且窗内 ⇒ 必须 005 免费");

        // 同站 + 超窗 ⇒ [006, 020]（原口径）
        assertEquals(List.of(AdviceOptEnum.PAID_UPDATE.getCode(), AdviceOptEnum.FREE_UPDATE_020.getCode()),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "0101", "0101", FREE_AREA, overWindow, "C1", "0101"),
                "同站超窗 ⇒ 付费更新在前");

        // 跨站 + 窗内 ⇒ 必须 006 付费（推翻 ADR-D136「窗内免费」）
        assertEquals(List.of(AdviceOptEnum.PAID_UPDATE.getCode(), AdviceOptEnum.FREE_UPDATE_020.getCode()),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "0101", "0101", FREE_AREA, withinWindow, "C1", "0202"),
                "跨站窗内也收费 ⇒ 006 必须排在首位");

        // 跨站 + 超窗 ⇒ 仍 006 付费
        assertEquals(List.of(AdviceOptEnum.PAID_UPDATE.getCode(), AdviceOptEnum.FREE_UPDATE_020.getCode()),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "0101", "0101", FREE_AREA, overWindow, "C1", "0202"),
                "跨站超窗同样 006 付费");

        // 站码未知（旧调用路径）⇒ 旧口径，窗内 005、超窗 [006,020]
        assertEquals(AdviceOptEnum.FREE_UPDATE.asSingletonList(),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "0101", "0101", FREE_AREA, withinWindow, "C1", null),
                "站码未知时回退旧口径：窗内 005");
        assertEquals(List.of(AdviceOptEnum.PAID_UPDATE.getCode(), AdviceOptEnum.FREE_UPDATE_020.getCode()),
                rules.resolveAdviceOpt(QRCodeStatusEnum.ENTRY, "0101", "0101", FREE_AREA, overWindow, "C1", null),
                "站码未知时回退旧口径：超窗 [006,020]");
    }

    @Test
    @DisplayName("站点感知（IF5A-03 执行侧）：跨站可走 005? 不可；跨站走 006? 可(含窗内)；同站窗内 006 必须拒")
    void stationAwareEnforcement() {
        rules.validateConfiguredDefaults();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String withinWindow = LocalDateTime.now().minusMinutes(5).format(formatter);
        String overWindow = LocalDateTime.now().minusMinutes(90).format(formatter);

        // 跨站 ⇒ 005 免费更新 MUST 拒绝
        assertEquals(SupplementStateRules.UpdateRejection.CROSS_STATION_NOT_FREE,
                rules.checkUpdate(QRCodeStatusEnum.ENTRY, AdviceOptEnum.FREE_UPDATE.getCode(),
                        FREE_AREA, withinWindow, "0101", "0202"),
                "跨站不可走免费更新，应让 BOM 重新分析拿到 006");

        // 同站 + 窗内 ⇒ 005 放行
        assertEquals(SupplementStateRules.UpdateRejection.NONE,
                rules.checkUpdate(QRCodeStatusEnum.ENTRY, AdviceOptEnum.FREE_UPDATE.getCode(),
                        FREE_AREA, withinWindow, "0101", "0101"),
                "同站窗内 ⇒ 免费更新放行");

        // 跨站 + 窗内 ⇒ 006 放行（即便在时间窗内也收费）
        assertEquals(SupplementStateRules.UpdateRejection.NONE,
                rules.checkUpdate(QRCodeStatusEnum.ENTRY, AdviceOptEnum.PAID_UPDATE.getCode(),
                        FREE_AREA, withinWindow, "0101", "0202"),
                "跨站窗内也放行付费更新（用户裁决）");

        // 同站 + 窗内 ⇒ 006 MUST 拒绝（应走免费的 005）
        assertEquals(SupplementStateRules.UpdateRejection.FREE_WINDOW_NOT_EXPIRED,
                rules.checkUpdate(QRCodeStatusEnum.ENTRY, AdviceOptEnum.PAID_UPDATE.getCode(),
                        FREE_AREA, withinWindow, "0101", "0101"),
                "同站窗内不该收费，拒绝 006");

        // 同站 + 超窗 ⇒ 006 放行
        assertEquals(SupplementStateRules.UpdateRejection.NONE,
                rules.checkUpdate(QRCodeStatusEnum.ENTRY, AdviceOptEnum.PAID_UPDATE.getCode(),
                        FREE_AREA, overWindow, "0101", "0101"),
                "同站超窗 ⇒ 付费更新放行");

        // 站码未知 + 窗内 ⇒ 沿用 ADR-D136：不收费（拒绝 006）
        assertEquals(SupplementStateRules.UpdateRejection.FREE_WINDOW_NOT_EXPIRED,
                rules.checkUpdate(QRCodeStatusEnum.ENTRY, AdviceOptEnum.PAID_UPDATE.getCode(),
                        FREE_AREA, withinWindow, "0101", null),
                "站码未知且窗内 ⇒ 证明不了超窗，NEVER 收费");

        // 站码未知 + 超窗 ⇒ 放行 006（旧口径）
        assertEquals(SupplementStateRules.UpdateRejection.NONE,
                rules.checkUpdate(QRCodeStatusEnum.ENTRY, AdviceOptEnum.PAID_UPDATE.getCode(),
                        FREE_AREA, overWindow, "0101", null),
                "站码未知但已确认超窗 ⇒ 放行付费更新");
    }
}
