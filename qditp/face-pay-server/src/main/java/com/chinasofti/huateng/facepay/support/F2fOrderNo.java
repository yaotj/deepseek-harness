package com.chinasofti.huateng.facepay.support;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** ITP 订单号格式。订单号固定 20 位，NEVER 加 {@code F2} 前缀。 */
public final class F2fOrderNo {

    /** 业务码：单程票（对应旧 {@code ProductType.ordinaryTicket}）。 */
    public static final String BIZ_SINGLE_TICKET = "00";

    /** 业务码：票卡充值（对应旧 {@code ProductType.tvmTopup}，旧号段 08）。 */
    public static final String BIZ_TOPUP = "08";

    /** 业务码：退款单。 */
    public static final String BIZ_REFUND = "09";

    /** 序列段位数，与 {@code F2F_ORDER_NO_SEQ} 的 MAXVALUE 9999 对应。 */
    public static final int SEQ_WIDTH = 4;

    private static final int SEQ_MODULO = 10000;

    private static final DateTimeFormatter TMS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private F2fOrderNo() {
    }

    /**
     * 拼订单号。
     *
     * @param bizCode 业务码，2 位
     * @param now     生成时刻
     * @param seq     序列值，取自 {@code F2F_ORDER_NO_SEQ}；超出 9999 时取模兜底
     */
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
