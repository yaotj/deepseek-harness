package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.chinasofti.huateng.paysign.support.PaySignValues;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class PayTxnDateResolutionTest {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Test
    void passedThroughDateIsUsed() {
        assertEquals("20250101", resolve("20250101"));
    }

    @Test
    void passedThroughDateIsKeptEvenWhenItIsNotToday() {
        String travelDate = "20250101";
        assertEquals(travelDate, resolve(travelDate),
                "行程日 MUST 原样透传，NEVER 用本地当日覆盖 —— 跨零点时两表就是靠这一列对齐的");
    }

    @Test
    void passedThroughDateIsTrimmed() {
        assertEquals("20250101", resolve("  20250101  "));
    }

    @Test
    void missingDateFallsBackToToday() {
        assertEquals(today(), resolve(null));
    }

    @Test
    void blankDateFallsBackToToday() {
        assertEquals(today(), resolve(""));
        assertEquals(today(), resolve("   "));
    }

    @Test
    void noArgVariantStillResolvesToToday() {
        assertEquals(today(), PaySignValues.resolveTxnDate());
    }

    private String today() {
        return LocalDate.now().format(YYYYMMDD);
    }

    private String resolve(String requestTxnDate) {
        return PaySignValues.resolveTxnDate(requestTxnDate);
    }
}
