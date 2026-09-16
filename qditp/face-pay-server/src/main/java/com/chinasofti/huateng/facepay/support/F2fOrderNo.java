package com.chinasofti.huateng.facepay.support;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ITP 订单号格式。<b>格式只在本类里定义一处</b>，NEVER 在别处散落字符串拼接。
 *
 * <h2>当前格式：20 位，与旧实现完全一致</h2>
 * <pre>
 * 00      (2)  业务码，取值同旧 ProductType，单程票 = 00
 * yyyyMMddHHmmss (14)
 * 0000    (4)  序列段，取 F2F_ORDER_NO_SEQ
 * </pre>
 *
 * <p>用户 2026-09-10 裁决「按照旧的来」：<b>不加版本标识前缀</b>。此前曾用 22 位
 * {@code F2 + 业务码 + 时间 + 序列}，但设备与支付中心收银台对该字段是否有长度限制未核实，
 * 长度变化是会直接打挂链路的 A 类契约差异，因此回退到旧口径。
 * 旧实现见 {@code OrderNoUtils.java:26-29}，序列取 {@code ORDER_NO_SEQ}。</p>
 *
 * <p><b>蓝绿并行期如何区分新旧服务产生的单</b>：不靠订单号，靠表——新服务只写 {@code F2F_*}，
 * 旧服务只写 {@code TBL_TVM_*}/{@code TBL_BOM_*}，两边零交集。单号撞车也不可能：两套服务
 * 用的是不同序列（{@code F2F_ORDER_NO_SEQ} vs {@code ORDER_NO_SEQ}），但<b>同一秒内两边各取到
 * 相同序列值时会生成相同订单号</b>——切换期是「停旧起新」而非同时收流量，因此不构成问题；
 * 若将来真要双写收流量，MUST 先解决这个碰撞。</p>
 *
 * <p><b>序列段定长由数据库保证</b>：{@code F2F_ORDER_NO_SEQ} 建成 {@code MAXVALUE 9999 CYCLE}，
 * 因此永远是 1~9999。Java 侧再做一次取模兜底——旧实现只 {@code leftPad} 不取模，序列一旦超过
 * 9999 就会吐出 21 位订单号，属于潜在缺陷，这里不继承。</p>
 */
public final class F2fOrderNo {

    /** 业务码：单程票（对应旧 {@code ProductType.ordinaryTicket}）。 */
    public static final String BIZ_SINGLE_TICKET = "00";

    /** 业务码：票卡充值（对应旧 {@code ProductType.tvmTopup}，旧号段 08）。 */
    public static final String BIZ_TOPUP = "08";

    /**
     * 业务码：退款单。
     *
     * <p>退款单号与订单号共用同一格式、同一序列，只靠业务码区分，好处是运维看号段即知单据类型，
     * 也不必再维护第二个序列。旧实现的退款单号由 {@code OrderCommonUtils} 另起一套规则，
     * 两者不兼容，但<b>退款单号不出现在设备契约里</b>（{@code requestRefund} 的成功响应只回
     * {@code retCode/retMsg}），因此换格式对设备无影响。</p>
     */
    public static final String BIZ_REFUND = "09";

    /** 序列段位数，与 {@code F2F_ORDER_NO_SEQ} 的 MAXVALUE 9999 对应。 */
    public static final int SEQ_WIDTH = 4;

    private static final int SEQ_MODULO = 10000;

    private static final DateTimeFormatter TMS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private F2fOrderNo() {
    }

    /**
     * 拼订单号。纯函数，时间由调用方传入，便于单测断言。
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
