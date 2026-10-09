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

/** {@code SupplementStateRules} 的特征测试（characterization test）：把 IF5A-01 建议侧与 IF5A-03 执行侧的整张规则表逐格钉死，为后续把两串 if 链改成表驱动做防护网。 */
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

    /** IF5A-01 建议侧全量矩阵。多个候选码用 {@code |} 分隔。 */
    @ParameterizedTest(name = "{0} + updateType={1} + gateIn={2}/{3} -> {4}")
    @CsvSource({
            "EXIT,                PAID, 0101, NONE,  018",
            "END_TRIP,            PAID, 0101, NONE,  018",
            "EXIT_OVERTIME,       PAID, 0101, NONE,  018",
            "SELF_SERVICE_EXIT,   PAID, 0101, NONE,  018",
            "EXIT,                FREE, 0101, NONE,  000",
            "ENTRY,               PAID, 0101, FRESH, 000",
            "ENTRY,               FREE, 0101, FRESH, 005",
            "ENTRY,               FREE, 0101, STALE, 006|020",
            "SELF_SERVICE_ENTRY,  FREE, 0101, FRESH, 005",
            "SELF_SERVICE_ENTRY,  FREE, 0101, STALE, 006|020",
            "ENTRY,               FREE, FFFF, STALE, 020",
            "ENTRY,               FREE, 0101, NONE,  020",
            "SELF_SERVICE_ENTRY,  FREE, 0101, NONE,  020",
            "SJT_ISSUE,           PAID, 0101, NONE,  018",
            "SJT_ISSUE,           FREE, 0101, NONE,  000",
            "UPDATE_FREE,         PAID, 0101, NONE,  018",
            "UPDATE_FREE,         FREE, 0101, FRESH, 000",
            "UPDATE_PAY,          PAID, 0101, NONE,  018",
            "UPDATE_PAY,          FREE, 0101, NONE,  000",
            "UPDATE_ENTRY,        PAID, FFFF, NONE,  018",
            "UPDATE_ENTRY,        PAID, 0101, NONE,  000",
            "UPDATE_ENTRY,        FREE, 0101, FRESH, 005",
            "UPDATE_ENTRY,        FREE, 0101, STALE, 000",
            "NO_TXN,              PAID, 0101, NONE,  000",
            "ABNORMAL,            PAID, 0101, NONE,  000",
            "ENTRY_FAIL,          PAID, 0101, NONE,  000",
    })
    void 建议侧矩阵逐格钉死(QRCodeStatusEnum codeStatus, String area, String gateInStation,
                          String timeMode, String expectedAdviceOpt) {
        List<String> actual = rules.resolveAdviceOpt(codeStatus, gateInStation, KNOWN_STATION,
                "PAID".equals(area) ? PAID_AREA : FREE_AREA, gateInTime(timeMode), "C1");
        assertEquals(List.of(expectedAdviceOpt.split("\\|")), actual);
    }

    /** IF5A-03 执行侧白名单全量矩阵。 */
    @ParameterizedTest(name = "{0} + adviceOpt={1} + updateType={2} + gateIn={3} -> {4}")
    @CsvSource({
            "EXIT,               018, PAID, NONE,  true",
            "END_TRIP,           018, PAID, NONE,  true",
            "SJT_ISSUE,          018, PAID, NONE,  true",
            "UPDATE_FREE,        018, PAID, NONE,  true",
            "UPDATE_PAY,         018, PAID, NONE,  true",
            "UPDATE_ENTRY,       018, PAID, NONE,  true",
            "EXIT,               018, FREE, NONE,  false",
            "SJT_ISSUE,          018, FREE, NONE,  false",
            "ENTRY,              018, PAID, NONE,  false",
            "SELF_SERVICE_ENTRY, 018, PAID, NONE,  false",
            "NO_TXN,             018, PAID, NONE,  false",
            "ENTRY,              006, FREE, STALE, true",
            "SELF_SERVICE_ENTRY, 006, FREE, STALE, true",
            "UPDATE_ENTRY,       006, FREE, STALE, true",
            "ENTRY,              006, FREE, FRESH, false",
            "UPDATE_ENTRY,       006, FREE, FRESH, false",
            "ENTRY,              006, FREE, NONE,  false",
            "ENTRY,              006, PAID, STALE, false",
            "EXIT,               006, FREE, STALE, false",
            "SJT_ISSUE,          006, FREE, STALE, false",
            "ENTRY,              005, FREE, FRESH, true",
            "UPDATE_ENTRY,       005, FREE, FRESH, true",
            "ENTRY,              005, FREE, STALE, false",
            "ENTRY,              005, FREE, NONE,  false",
            "ENTRY,              005, PAID, FRESH, false",
            "EXIT,               005, FREE, FRESH, false",
            "ENTRY,              020, FREE, STALE, true",
            "ENTRY,              020, FREE, NONE,  true",
            "SELF_SERVICE_ENTRY, 020, FREE, STALE, true",
            "UPDATE_ENTRY,       020, FREE, STALE, true",
            "ENTRY,              020, PAID, STALE, false",
            "EXIT,               020, FREE, STALE, false",
            "SJT_ISSUE,          020, FREE, STALE, false",
            "ENTRY,              000, FREE, FRESH, false",
            "EXIT,               000, PAID, NONE,  false",
    })
    void 执行侧白名单矩阵逐格钉死(QRCodeStatusEnum codeStatus, String adviceOpt, String area,
                              String timeMode, boolean expected) {
        boolean actual = rules.isUpdateAllowed(codeStatus, adviceOpt,
                "PAID".equals(area) ? PAID_AREA : FREE_AREA, gateInTime(timeMode));
        assertEquals(expected, actual);
    }

    /** 两侧对称性：建议侧不给 {@code 018} 的组合，执行侧 */
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
