package com.chinasofti.huateng.model.recon;

/**
 * face-pay 侧 TXNDIFF 数据源（F2F_ORDER JOIN F2F_PAYMENT(SUCCESS)）的分片行编解码。
 *
 * <p>通道：recon-server 不直接连 face-pay 库（拆库在即、无 SELECT 授权，见 recon-thirdparty.md §八），
 * 改由 face-pay-server 在对账抽取指令里把这笔流水写成分片、经 {@code ReconPartSink} 上送 recon-server 落盘，
 * 再由 {@code FacePayTxnShardFetcher} 按账期取回。本类是这条分片行格式的唯一契约，两端共用。</p>
 *
 * <p>字段顺序（21 段，与 {@code ItpTxnRecord} 的 1~18 数据段 + 商户订单号 + 渠道交易号 + ITP 侧终态严格对齐）：
 * <ol>
 *   <li>0 交易类型</li>
 *   <li>1 逻辑卡号</li>
 *   <li>2 交易日期(yyyyMMddHHmmss)</li>
 *   <li>3 交易原价(分)</li>
 *   <li>4 当前车站名称/编码</li>
 *   <li>5 设备编码</li>
 *   <li>6 票卡类型</li>
 *   <li>7 票卡子类型</li>
 *   <li>8 次数</li>
 *   <li>9 支付渠道</li>
 *   <li>10 激活时间</li>
 *   <li>11 数量</li>
 *   <li>12 交易流水号</li>
 *   <li>13 交易订单号</li>
 *   <li>14 订单金额(分)</li>
 *   <li>15 实际扣款金额(分)</li>
 *   <li>16 优惠累计消费金额(分)</li>
 *   <li>17 超时金额(分)</li>
 *   <li>18 商户订单号（= F2F_ORDER.ORDER_NO，即支付中心 merchantOrderNo）</li>
 *   <li>19 渠道交易号（= F2F_PAYMENT.CHANNEL_ORDER_NO）</li>
 *   <li>20 ITP 侧终态（SUCCESS / FAIL）</li>
 * </ol>
 * 段 18/19 为与渠道账单的关联键，必须与 {@code ThirdPartyBill.joinKey()} 同构（商户订单号 \u0001 渠道交易号）。</p>
 */
public final class F2fTxnLine {

    /** 字段总段数。 */
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

    /**
     * 由 21 段字段拼成一行；字段数与顺序 MUST 与 {@link #FIELD_COUNT} 一致。
     * @param fields 长度必须为 {@link #FIELD_COUNT} 的字符串数组，null 会写成空串。
     * @return 管道分隔的一行。
     */
    public static String toLine(String... fields) {
        if (fields == null || fields.length != FIELD_COUNT) {
            throw new IllegalArgumentException("F2F_TXN 分片行段数必须为 " + FIELD_COUNT
                    + "，实际=" + (fields == null ? "null" : fields.length));
        }
        return ReconRecord.line(fields);
    }

    /**
     * 拆解一行；不足 {@link #FIELD_COUNT} 段时尾部补空串，便于容错解析。
     * @param line 管道分隔的一行。
     * @return 长度恰为 {@link #FIELD_COUNT} 的字段数组。
     */
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
