package com.chinasofti.huateng.model.recon;

/**
 * 日终对账文件类型（ACC 与 ITP 之间的四类对账文件）。
 *
 * <p>四类文件的分片内部格式统一为「管道分隔文本、UTF-8、行尾 \n、无表头」，字段拼装与拆解统一
 * 走 {@link ReconRecord}。EXP / DETAIL 是明细文件，recon-server 只做流式字节拼接；
 * PAY / BUS 是汇总文件，源服务 MUST 在数据库内 GROUP BY 后再上送，recon-server 按
 * {@link #getKeyFieldCount()} 段键聚合、对随后的 {@link #getMetricFieldCount()} 个度量列做累加。</p>
 *
 * <p><b>字段顺序来自甲方 {@code docs/接口规范文档/ACC与ITP之间的文件.docx} §一「对账文件」，
 * 改动 MUST 同步四个源服务与 recon-server</b>——分片是纯文本、无 schema，错位不会报错，
 * 只会静默出错账。</p>
 *
 * <h2>ITP.EXP 单边交易明细文件（明细类，13 段）</h2>
 * <pre>
 * 1 逻辑卡号
 * 2 票卡交易序列号
 * 3 订单金额（单位分）
 * 4 实际扣款金额（单位分）
 * 5 优惠金额
 * 6 进站设备编码
 * 7 进站时间
 * 8 出站处理设备类型
 * 9 出站设备编码
 * 10 出站时间
 * 11 订单异常类型
 * 12 支付方式（同交易明细）
 * 13 交易日期
 * </pre>
 * <p>第 11 段「订单异常类型」取值 1~15：1 单边账(入站)、2 单边账(出站)、3 单边入站(人工处理单)、
 * 4 单边出站(人工处理单)、5 乘客自主补进站、6 乘客自主补出站、7 TVM 补币找零不足、8 TVM 卡票、
 * 9 TVM/BOM 发售无效票、10 闸门无用、11 无票出闸、12 人为单程票无效、13 非人为单程票无效、
 * 14 储值票无效、15 其他情况。<b>EXP 是「单边账 / 异常交易」明细，NEVER 当成全量过闸明细。</b></p>
 *
 * <h2>ITP.PAY 统计汇总文件（聚合类，21 段 = 5 段键 + 16 段度量）</h2>
 * <pre>
 * 键   1 日期 | 2 线路 | 3 车站 | 4 设备编号 | 5 支付方式（同交易明细）
 * 度量 6 BOM/TVM发售笔数    | 7 BOM/TVM发售金额
 *      8 BOM/TVM充值笔数    | 9 BOM/TVM充值金额
 *      10 旅游票张数(发售)   | 11 旅游票金额
 *      12 过闸笔数          | 13 过闸金额
 *      14 APP购票笔数       | 15 APP购票金额
 *      16 BOM行政处理笔数    | 17 BOM行政处理金额
 *      18 单边交易笔数       | 19 单边交易金额
 *      20 BOM处理笔数       | 21 BOM处理金额
 * </pre>
 *
 * <h2>ITP.BUS 商业优惠汇总文件（聚合类，4 段 = 1 段键 + 3 段度量）</h2>
 * <pre>
 * 键   1 日期
 * 度量 2 对账金额 | 3 付款金额 | 4 优惠金额
 * </pre>
 *
 * <h2>ITP.DETAIL 虚拟电子多日计次票文件（明细类，7 段）</h2>
 * <pre>
 * 1 运营日期
 * 2 交易类型
 * 3 逻辑卡号
 * 4 交易日期时间
 * 5 交易金额
 * 6 当前车站名称
 * 7 设备编码
 * </pre>
 * <p>规则：多日计次票正常过闸不对账，只对「车票购买」与「超时费」两类。车票购买的交易类型记为
 * 发售，第 6 段当前车站名称与第 7 段设备编码传空；超时行程的交易类型记为出站，费用为线网最高
 * 票价或 1。</p>
 *
 * <h2>账期与窗口</h2>
 * <p>T 日 2 点统计 T-2 日 2 点 ~ T-1 日 2 点，文件名后缀取 T-2 日
 * （甲方例：8 月 20 号 2 点生成 {@code ITP.EXP.20190818}）。FTP 固定路径 {@code /itp/recon/}。</p>
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
     *
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
     *
     * @param name 文件类型名，大小写不敏感
     * @return 文件类型
     */
    public static ReconFileTypeEnum of(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("对账文件类型为空");
        }
        return valueOf(name.trim().toUpperCase());
    }
}
