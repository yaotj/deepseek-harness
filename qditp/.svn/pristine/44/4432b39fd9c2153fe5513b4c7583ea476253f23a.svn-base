package com.chinasofti.huateng.acc.security.server.itp.util;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class ItpCardUtils {
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    private ItpCardUtils() {
    }

    public static String buildLogicNumber(int sequence) {
        String prefix = "04" + LocalDate.now().format(YYMMDD) + String.format("%07d", sequence);
        return prefix + calcCheckDigit(prefix);
    }

    public static byte[] getIssueDate(String dateStr) {
        int year = Integer.parseInt(dateStr.substring(0, 2));
        int month = Integer.parseInt(dateStr.substring(2, 4));
        int day = Integer.parseInt(dateStr.substring(4, 6));
        byte[] result = new byte[2];
        result[0] = (byte) ((year << 1) & 0xFE);
        result[0] |= (byte) ((month >> 3) & 0x01);
        result[1] = (byte) ((month << 5) & 0xE0);
        result[1] |= (byte) (day & 0x1F);
        return result;
    }

    public static long fixedDateSecond() {
        return LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.ofHours(8)).toEpochSecond();
    }

    private static int calcCheckDigit(String source) {
        int sum = 0;
        for (char c : source.toCharArray()) {
            sum += c - '0';
        }
        return sum % 10;
    }
}
