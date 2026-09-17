package com.chinasofti.huateng.model.recon;

/**
 * 日终对账文件类型（ACC 与 ITP 之间的四类对账文件）。
 */
public enum ReconFileTypeEnum {

    /** 单边交易明细文件，13 段，明细类不聚合。 */
    EXP("ITP.EXP", false, 0, 0),

    /** 统计汇总文件，21 段：前 5 段键 + 16 段度量（8 组「笔数, 金额」）。 */
    PAY("ITP.PAY", true, 5, 16),

    /** 商业优惠汇总文件，4 段：前 1 段键（日期）+ 3 段金额度量。 */
    BUS("ITP.BUS", true, 1, 3),

    /** 虚拟电子多日计次票明细文件，7 段，明细类不聚合。 */
    DETAIL("ITP.DETAIL", false, 0, 0);

    /** ITP.EXP 的字段总段数，甲方规格固定 13 段。 */
    private static final int EXP_FIELD_COUNT = 13;

    /** ITP.DETAIL 的字段总段数，甲方规格固定 7 段。 */
    private static final int DETAIL_FIELD_COUNT = 7;

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

    /** 最终文件名前缀，完整文件名为 {@code <prefix>.<yyyyMMdd>}。 */
    public String getPrefix() {
        return prefix;
    }

    /** 是否为汇总文件（需要 recon-server 二次聚合）。 */
    public boolean isAggregate() {
        return aggregate;
    }

    /** 汇总文件的键字段个数（PAY 为 5、BUS 为 1）；明细文件为 0。 */
    public int getKeyFieldCount() {
        return keyFieldCount;
    }

    /** 汇总文件的度量字段个数（PAY 为 16、BUS 为 3），紧跟在键字段之后；明细文件为 0。 */
    public int getMetricFieldCount() {
        return metricFieldCount;
    }

    /**
     * 该类型每行的字段总段数：汇总文件为键 + 度量之和，明细文件取甲方规格固定值。
     * @return 字段总段数（EXP 13、PAY 21、BUS 4、DETAIL 7）
     */
    public int getFieldCount() {
        return switch (this) {
            case EXP -> EXP_FIELD_COUNT;
            case DETAIL -> DETAIL_FIELD_COUNT;
            case PAY, BUS -> keyFieldCount + metricFieldCount;
        };
    }

    /**
     * 按名称解析文件类型，无法识别时抛异常而不是返回 null。
     * @param name 文件类型名，大小写不敏感。
     * @return 文件类型。
     */
    public static ReconFileTypeEnum of(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("对账文件类型为空");
        }
        return valueOf(name.trim().toUpperCase());
    }
}
