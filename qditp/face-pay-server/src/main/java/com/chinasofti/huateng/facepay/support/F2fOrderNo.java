package com.chinasofti.huateng.facepay.support;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class F2fOrderNo {

    public static final String BIZ_SINGLE_TICKET = "00";

    public static final String BIZ_TOPUP = "08";

    public static final String BIZ_REFUND = "09";

    public static final int SEQ_WIDTH = 4;

    private static final int SEQ_MODULO = 10000;

    private static final DateTimeFormatter TMS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private F2fOrderNo() {
    }

    public static String format(String bizCode, LocalDateTime now, long seq) {
        if (bizCode == null || bizCode.length() != 2) {
            throw new IllegalArgumentException("bizCode 必须是 2 位, bizCode=" + bizCode);
        }
        if (now == null) {
            throw new IllegalArgumentException("now 必填");
        }
        if (seq < 0) {
            throw new IllegalArgumentException("seq 不能为负, seq=" + seq);
        }
        String seqSegment = String.valueOf(seq % SEQ_MODULO);
        StringBuilder sb = new StringBuilder(20);
        sb.append(bizCode).append(now.format(TMS_FORMATTER));
        for (int i = seqSegment.length(); i < SEQ_WIDTH; i++) {
            sb.append('0');
        }
        return sb.append(seqSegment).toString();
    }
}
