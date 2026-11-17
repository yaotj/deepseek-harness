package com.chinasofti.huateng.model.recon;


public final class F2fTxnLine {

    public static final int FIELD_COUNT = 21;

    public static final int TXN_TYPE = 0;
    public static final int LOGIC_CARD_NO = 1;
    public static final int TXN_DATE_TIME = 2;
    public static final int ORIGINAL_FARE = 3;
    public static final int CUR_STATION_NAME = 4;
    public static final int DEVICE_NO = 5;
    public static final int TICKET_TYPE = 6;
    public static final int TICKET_SUB_TYPE = 7;
    public static final int TIMES = 8;
    public static final int PAY_CHANNEL = 9;
    public static final int ACTIVATE_TIME = 10;
    public static final int QUANTITY = 11;
    public static final int TICKET_TRANS_SEQ = 12;
    public static final int ORDER_NO = 13;
    public static final int ORDER_AMOUNT = 14;
    public static final int ACTUAL_AMOUNT = 15;
    public static final int DISCOUNT_CUMULATIVE_AMOUNT = 16;
    public static final int OVERTIME_AMOUNT = 17;
    public static final int MERCHANT_ORDER_NO = 18;
    public static final int CHANNEL_TRADE_NO = 19;
    public static final int ITP_PAY_STATUS = 20;

    private F2fTxnLine() {
    }


    public static String toLine(String... fields) {
        if (fields == null || fields.length != FIELD_COUNT) {
            throw new IllegalArgumentException("F2F_TXN 分片行段数必须为 " + FIELD_COUNT
                    + "，实际=" + (fields == null ? "null" : fields.length));
        }
        return ReconRecord.line(fields);
    }


    public static String[] parse(String line) {
        String[] parts = ReconRecord.split(line);
        if (parts.length == FIELD_COUNT) {
            return parts;
        }
        String[] fixed = new String[FIELD_COUNT];
        System.arraycopy(parts, 0, fixed, 0, Math.min(parts.length, FIELD_COUNT));
        return fixed;
    }
}
