package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.ticket.enums.AdviceOptEnum;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * {@code SupplementStateRules} 的**特征测试**（characterization test）：把 IF5A-01 建议侧与
 * IF5A-03 执行侧的**整张规则表**逐格钉死，为后续把两串 if 链改成表驱动做防护网。
 *
 * <p>与同包 {@code SupplementStateRulesTest} 的分工：那个类钉的是 2026-09-14 审查里五条
 * **具体修复**（C001 / M003 / X001 / C002 / M007，改回去就变红）；本类钉的是**全量矩阵**，
 * 用来发现「重构时某一格悄悄换了结果」。有重叠是有意的，<b>NEVER</b> 为去重删掉任何一边。</p>
 *
 * <p>两张表的**对称性**本身就是不变量：建议侧不给的组合，执行侧 MUST 也不放行（X001 就是
 * 这条对称性被破坏的实例）。因此本类最后一组用例直接断言两侧同口径。</p>
 */
class SupplementStateRulesMatrixTest {

    private static final String PAID_AREA = "01";
    private static final String FREE_AREA = "00";
    private static final String UNKNOWN_STATION = "FFFF";
    private static final String KNOWN_STATION = "0101";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private SupplementStateRules rules;

    @BeforeEach
    void setUp() {
        rules = new SupplementStateRules();
        ReflectionTestUtils.setField(rules, "defaultCodeStatus", "03");
        ReflectionTestUtils.setField(rules, "defaultLastTxnStation", UNKNOWN_STATION);
        rules.validateConfiguredDefaults();
    }

    /** {@code FRESH} 在 20 分钟窗内，{@code STALE} 已超窗，{@code NONE} 表示未上送。 */
    private String gateInTime(String mode) {
        return switch (mode) {
            case "FRESH" -> LocalDateTime.now().minusMinutes(5).format(FORMATTER);
            case "STALE" -> LocalDateTime.now().minusHours(2).format(FORMATTER);
            case "NONE" -> null;
            default -> throw new IllegalArgumentException("未知的时间档位: " + mode);
        };
    }

    /**
     * IF5A-01 建议侧全量矩阵。
     *
     * <p>{@code isClosedLoop()} = 02 / 05 / 06 / 80，{@code isOpenLoop()} = 04 / 81，
     * 两组各只取一个代表 + 一个边界值，其余状态逐个列出。</p>
     */
    @ParameterizedTest(name = "{0} + updateType={1} + gateIn={2}/{3} -> {4}")
    @CsvSource({
            // 闭环：付费区补进站；非付费区人在闸外且卡上无未闭合进站 ⇒ 020 免费进闸更新（2026-09-15 起，此前 000）
            "EXIT,                PAID, 0101, NONE,  018",
            "END_TRIP,            PAID, 0101, NONE,  018",
            "EXIT_OVERTIME,       PAID, 0101, NONE,  018",
            "SELF_SERVICE_EXIT,   PAID, 0101, NONE,  018",
            "EXIT,                FREE, 0101, NONE,  020",
            // 开环：付费区不建议；非付费区窗内免费更新、超窗付费更新。NEVER 换成 020（会覆盖原进站基准）
            "ENTRY,               PAID, 0101, FRESH, 000",
            "ENTRY,               FREE, 0101, FRESH, 005",
            "ENTRY,               FREE, 0101, STALE, 006",
            "SELF_SERVICE_ENTRY,  FREE, 0101, FRESH, 005",
            "SELF_SERVICE_ENTRY,  FREE, 0101, STALE, 006",
            // M007：超窗但进站站未知 ⇒ 报不出价 ⇒ 不给 006
            "ENTRY,               FREE, FFFF, STALE, 000",
            // 03 新卡：付费区补进站，非付费区 020
            "SJT_ISSUE,           PAID, 0101, NONE,  018",
            "SJT_ISSUE,           FREE, 0101, NONE,  020",
            // 08 / 09 已更新过：只看付费区，不看时间窗
            "UPDATE_FREE,         PAID, 0101, NONE,  018",
            "UPDATE_FREE,         FREE, 0101, FRESH, 020",
            "UPDATE_PAY,          PAID, 0101, NONE,  018",
            "UPDATE_PAY,          FREE, 0101, NONE,  020",
            // 10 入站码更新：付费区看进站站是否已知，非付费区看时间窗
            "UPDATE_ENTRY,        PAID, FFFF, NONE,  018",
            "UPDATE_ENTRY,        PAID, 0101, NONE,  000",
            "UPDATE_ENTRY,        FREE, 0101, FRESH, 005",
            "UPDATE_ENTRY,        FREE, 0101, STALE, 000",
            // 其余状态一律 000
            "NO_TXN,              PAID, 0101, NONE,  000",
            "ABNORMAL,            PAID, 0101, NONE,  000",
            "ENTRY_FAIL,          PAID, 0101, NONE,  000",
    })
    void 建议侧矩阵逐格钉死(QRCodeStatusEnum codeStatus, String area, String gateInStation,
                          String timeMode, String expectedAdviceOpt) {
        List<String> actual = rules.resolveAdviceOpt(codeStatus, gateInStation, KNOWN_STATION,
                "PAID".equals(area) ? PAID_AREA : FREE_AREA, gateInTime(timeMode), "C1");
        assertEquals(List.of(expectedAdviceOpt), actual);
    }

    /**
     * IF5A-03 执行侧白名单全量矩阵。
     *
     * <p>三个建议操作各有各的 {@code updateType} 要求：{@code 018} 只在**付费区**、
     * {@code 005} / {@code 006} 只在**非付费区**。{@code 005} 额外复核 20 分钟时间窗。</p>
     */
    @ParameterizedTest(name = "{0} + adviceOpt={1} + updateType={2} + gateIn={3} -> {4}")
    @CsvSource({
            // 018 补进站：闭环 / 03 / 08 / 09 / 10 且付费区
            "EXIT,               018, PAID, NONE,  true",
            "END_TRIP,           018, PAID, NONE,  true",
            "SJT_ISSUE,          018, PAID, NONE,  true",
            "UPDATE_FREE,        018, PAID, NONE,  true",
            "UPDATE_PAY,         018, PAID, NONE,  true",
            "UPDATE_ENTRY,       018, PAID, NONE,  true",
            "EXIT,               018, FREE, NONE,  false",
            "SJT_ISSUE,          018, FREE, NONE,  false",
            // 018 对开环状态一律拒绝：已进站的码不能再补进站
            "ENTRY,              018, PAID, NONE,  false",
            "SELF_SERVICE_ENTRY, 018, PAID, NONE,  false",
            "NO_TXN,             018, PAID, NONE,  false",
            // 006 付费更新：开环 / 10 且非付费区，不设时间下限
            "ENTRY,              006, FREE, STALE, true",
            "SELF_SERVICE_ENTRY, 006, FREE, STALE, true",
            "UPDATE_ENTRY,       006, FREE, NONE,  true",
            "ENTRY,              006, PAID, STALE, false",
            "EXIT,               006, FREE, STALE, false",
            "SJT_ISSUE,          006, FREE, STALE, false",
            // 005 免费更新：同 006 的状态 / 区域要求，外加时间窗
            "ENTRY,              005, FREE, FRESH, true",
            "UPDATE_ENTRY,       005, FREE, FRESH, true",
            "ENTRY,              005, FREE, STALE, false",
            "ENTRY,              005, FREE, NONE,  false",
            "ENTRY,              005, PAID, FRESH, false",
            "EXIT,               005, FREE, FRESH, false",
            // 000 无需操作本身不是可执行的操作
            "ENTRY,              000, FREE, FRESH, false",
            "EXIT,               000, PAID, NONE,  false",
    })
    void 执行侧白名单矩阵逐格钉死(QRCodeStatusEnum codeStatus, String adviceOpt, String area,
                              String timeMode, boolean expected) {
        boolean actual = rules.isUpdateAllowed(codeStatus, adviceOpt,
                "PAID".equals(area) ? PAID_AREA : FREE_AREA, gateInTime(timeMode));
        assertEquals(expected, actual);
    }

    /**
     * 两侧对称性：<b>建议侧不给 {@code 018} 的组合，执行侧 MUST 也拒绝 {@code 018}</b>。
     *
     * <p>X001 就是这条对称性被破坏的实例（执行侧对 03 新卡无条件放行、建议侧从不给 018）。
     * 重构时若把某一侧的条件改宽，本用例会变红。</p>
     *
     * <p>2026-09-15 起，非付费区那几格建议侧给的是 {@code 020} 而不再是 {@code 000}
     * （见建议侧矩阵），因此本用例只断言「不是 018」，<b>NEVER 退回成断言恒等于 000</b> ——
     * 那会把「补进站方向只在付费区」这条真正的不变量偷换成一个已经不成立的前提。</p>
     */
    @ParameterizedTest(name = "{0} + updateType={1}")
    @CsvSource({
            "SJT_ISSUE,          FREE",
            "EXIT,               FREE",
            "UPDATE_FREE,        FREE",
            "UPDATE_PAY,         FREE",
            "ENTRY,              PAID",
            "SELF_SERVICE_ENTRY, PAID",
            "NO_TXN,             PAID",
            "ABNORMAL,           PAID",
    })
    void 建议侧不给018的组合执行侧也MUST拒绝018(QRCodeStatusEnum codeStatus, String area) {
        String updateType = "PAID".equals(area) ? PAID_AREA : FREE_AREA;
        assertNotEquals(AdviceOptEnum.SUPPLEMENT_ENTRY.asSingletonList(),
                rules.resolveAdviceOpt(codeStatus, KNOWN_STATION, KNOWN_STATION, updateType, null, "C1"),
                "本用例前提是建议侧不给 018；若这里变红说明矩阵前提已变，先回头看上面两张表");
        assertEquals(false, rules.isUpdateAllowed(codeStatus,
                AdviceOptEnum.SUPPLEMENT_ENTRY.getCode(), updateType, null));
    }
}
