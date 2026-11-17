package com.chinasofti.huateng.ticket.gate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GateCodeStatusResolverTest {

    private GateCodeStatusResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new GateCodeStatusResolver();
        ReflectionTestUtils.setField(resolver, "defaultCodeStatus", "03");
    }

    @ParameterizedTest(name = "trxType={0}, excessFareType={1}, adviceOpt={2} -> {3}")
    @CsvSource(nullValues = "NULL", value = {
            "NULL, NULL, 018, 04",
            "NULL, NULL, 020, 08",
            "NULL, NULL, 005, 08",
            "NULL, NULL, 006, 09",
            "NULL, 01,   NULL, 81",
            "NULL, 02,   NULL, 80",
            "01,   NULL, NULL, 04",
            "02,   NULL, NULL, 05",
            "03,   NULL, NULL, 06",
            "04,   NULL, NULL, FF",
            "99,   NULL, NULL, 70",
    })
    void 映射表逐格钉死(String trxType, String excessFareType, String adviceOpt, String expected) {
        assertEquals(expected, resolver.resolveCodeStatus(trxType, excessFareType, adviceOpt));
    }

    @ParameterizedTest(name = "trxType={0}, excessFareType={1}, adviceOpt={2} -> {3}")
    @CsvSource(nullValues = "NULL", value = {
            "02, 02, 006, 09",
            "01, 01, 018, 04",
            "01, 01, NULL, 81",
            "02, 02, NULL, 80",
    })
    void 优先级_adviceOpt大于excessFareType大于trxType(String trxType, String excessFareType,
                                                     String adviceOpt, String expected) {
        assertEquals(expected, resolver.resolveCodeStatus(trxType, excessFareType, adviceOpt));
    }

    @ParameterizedTest(name = "trxType={0}, excessFareType={1}, adviceOpt={2} -> {3}")
    @CsvSource(nullValues = "NULL", value = {
            "01, '',   '',   04",
            "01, '  ', '  ', 04",
            "02, NULL, 000, 05",
            "03, 03,   NULL, 06",
            "04, 04,   NULL, FF",
    })
    void 未命中即逐级回落(String trxType, String excessFareType, String adviceOpt, String expected) {
        assertEquals(expected, resolver.resolveCodeStatus(trxType, excessFareType, adviceOpt));
    }

    @Test
    void 三级全不命中时落配置的兜底值() {
        assertEquals("03", resolver.resolveCodeStatus("88", null, null));
        assertEquals("03", resolver.resolveCodeStatus(null, null, null));
    }

    @Test
    void 兜底值配错时热路径抛NPE_属已知地雷(){
        ReflectionTestUtils.setField(resolver, "defaultCodeStatus", "ZZ");
        assertThrows(NullPointerException.class, () -> resolver.resolveCodeStatus("88", null, null));
    }
}
