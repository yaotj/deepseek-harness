package com.chinasofti.huateng.model.recon;


public enum ReconFileTypeEnum {

    EXP("ITP.EXP", false, 0, 0),

    PAY("ITP.PAY", true, 5, 16),

    BUS("ITP.BUS", true, 1, 3),


    DETAIL("ITP.DETAIL", false, 0, 0),


    F2F_TXN("ITP.F2F_TXN", false, 0, 0);

    private static final int EXP_FIELD_COUNT = 13;

    private static final int DETAIL_FIELD_COUNT = 21;

    private static final int F2F_FIELD_COUNT = 21;

    private final String prefix;

    private final boolean aggregate;

    private final int keyFieldCount;

    private final int metricFieldCount;

    ReconFileTypeEnum(String prefix, boolean aggregate, int keyFieldCount, int metricFieldCount) {
        this.prefix = prefix;
        this.aggregate = aggregate;
        this.keyFieldCount = keyFieldCount;
        this.metricFieldCount = metricFieldCount;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isAggregate() {
        return aggregate;
    }

    public int getKeyFieldCount() {
        return keyFieldCount;
    }

    public int getMetricFieldCount() {
        return metricFieldCount;
    }


    public int getFieldCount() {
        return switch (this) {
            case EXP -> EXP_FIELD_COUNT;
            case DETAIL, F2F_TXN -> F2F_FIELD_COUNT;
            case PAY, BUS -> keyFieldCount + metricFieldCount;
        };
    }


    public static ReconFileTypeEnum of(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("对账文件类型为空");
        }
        return valueOf(name.trim().toUpperCase());
    }
}
