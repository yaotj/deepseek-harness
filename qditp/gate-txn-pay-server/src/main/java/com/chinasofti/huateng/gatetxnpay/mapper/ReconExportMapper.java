package com.chinasofti.huateng.gatetxnpay.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 日终对账抽取专用查询，只读 {@code GATE_TXN_PAY}，不复用任何联机链路的 mapper。
 *
 * <p>刻意独立成一个 mapper 而不是往 {@link GateTxnPayMapper} 里加方法：对账抽取是批处理口径
 * （全量扫时间窗口、按 keyset 翻页、库内 GROUP BY），与联机查询的口径和索引策略完全不同，
 * 混在一起后续任何一方调整过滤条件都会误伤另一方。</p>
 *
 * <p>返回类型统一用 {@code Map<String,Object>} 而非 {@code GateTxnPay} 实体：抽取每类文件只需要
 * 7~10 列，映射成 40 余列的实体等于让每行多背 30 个 null 字段，百万行量级下是可观的无谓开销。
 * <b>Oracle 的 NUMBER 列在 Map 里是 {@code BigDecimal}</b>，取值 MUST 经调用方的转换方法。</p>
 *
 * <p>本模块按甲方《ACC与ITP之间的文件》§一 产出四类文件，各自口径不同，<b>NEVER 互相套用</b>：
 * <ul>
 *   <li>{@code ITP.EXP} 单边交易明细 —— 只取异常/单边订单（{@code ORDER_EXP_TYPE} 非空非空格）</li>
 *   <li>{@code ITP.PAY} 统计汇总 —— 过闸组取 {@code DEBIT_STATUS='SUCCESS'}，单边组取异常订单</li>
 *   <li>{@code ITP.BUS} 商业优惠汇总 —— 只取 {@code DEBIT_STATUS='SUCCESS'}，按交易日汇总</li>
 *   <li>{@code ITP.DETAIL} 虚拟电子多日计次票明细 —— 只取日票/计次票且产生超时费的行程</li>
 * </ul>
 * </p>
 */
@Mapper
public interface ReconExportMapper {

    /**
     * EXP 单边交易明细抽取，keyset（游标）分页。
     *
     * <p><b>NEVER 改成 OFFSET 大页码翻页</b>：Oracle 的 {@code OFFSET n ROWS} 需要先产出并丢弃
     * 前 n 行，第 800 页的代价是第 1 页的 800 倍，全量扫完是 O(n²)。本方法用
     * {@code (OUT_TIME, ID)} 复合游标，每批都是索引区间的第一批，代价恒定。</p>
     *
     * <p>{@code startDate} / {@code endDate} 取 {@code ReconExportReqDTO.getStartDate()} 与
     * {@code getEndDate()}：对账窗口是 T-2 02:00 到 T-1 02:00，<b>横跨两个 yyyyMMdd</b>，
     * 只传一个日期会丢掉窗口后半段。这两个参数是月分区表的分区裁剪条件，缺失即全分区扫描。</p>
     *
     * @param startDate    交易日期下限（含），{@code yyyyMMdd}
     * @param endDate      交易日期上限（含），{@code yyyyMMdd}
     * @param windowStart  出站时间下限（含），{@code yyyyMMddHHmmss}
     * @param windowEnd    出站时间上限（不含），{@code yyyyMMddHHmmss}
     * @param lastOutTime  上一批末行的 {@code OUT_TIME}，首批传 null
     * @param lastId       上一批末行的 {@code ID}，首批传 null
     * @param limit        本批最大行数
     * @return 每行含 ID / CARD_ID / TICKET_TRANS_SEQ / TOTAL_AMOUNT / IN_TIME / DEVICE_ID /
     *         OUT_TIME / ORDER_EXP_TYPE / SIGN_CHANNEL_CODE / TXN_DATE
     */
    List<Map<String, Object>> selectExpPage(@Param("startDate") String startDate,
                                            @Param("endDate") String endDate,
                                            @Param("windowStart") String windowStart,
                                            @Param("windowEnd") String windowEnd,
                                            @Param("lastOutTime") String lastOutTime,
                                            @Param("lastId") Long lastId,
                                            @Param("limit") int limit);

    /**
     * PAY 汇总的「过闸」两段度量，<b>在数据库内 GROUP BY</b>，一次查回全部结果。
     *
     * <p>不做分页：按「交易日 / 出站车站 / 设备 / 签约通道」四键聚合后只有几百到几千行，
     * 分页反而要把同一个聚合跑多遍。<b>NEVER 改成把明细拉回 Java 再聚合</b>——那等于把
     * 百万行搬进堆内存，本方法存在的唯一理由就是避免这件事。</p>
     *
     * <p>过滤口径 {@code DEBIT_STATUS='SUCCESS'}，与 {@link #selectBusSummary} 一致：都是「已结清」
     * 的过闸收入。<b>与 {@link #selectExpPage} / {@link #selectPayExpSummary} 的异常订单口径是两回事</b>，
     * NEVER 互相复制 WHERE。</p>
     *
     * @return 每行含 TXN_DATE / OUT_STATION / DEVICE_ID / SIGN_CHANNEL_CODE / TXN_COUNT / PAY_AMOUNT
     */
    List<Map<String, Object>> selectPayGateSummary(@Param("startDate") String startDate,
                                                    @Param("endDate") String endDate,
                                                    @Param("windowStart") String windowStart,
                                                    @Param("windowEnd") String windowEnd);

    /**
     * PAY 汇总的「单边交易」两段度量，键与度量结构同 {@link #selectPayGateSummary}，
     * 只有 WHERE 不同：这里筛 {@code ORDER_EXP_TYPE} 非空非空格的异常/单边订单。
     *
     * <p>过滤口径 MUST 与 {@link #selectExpPage} 逐字一致，否则 PAY 里的单边笔数金额与 EXP 明细
     * 对不上账。</p>
     *
     * @return 每行含 TXN_DATE / OUT_STATION / DEVICE_ID / SIGN_CHANNEL_CODE / TXN_COUNT / PAY_AMOUNT
     */
    List<Map<String, Object>> selectPayExpSummary(@Param("startDate") String startDate,
                                                   @Param("endDate") String endDate,
                                                   @Param("windowStart") String windowStart,
                                                   @Param("windowEnd") String windowEnd);

    /**
     * BUS 商业优惠汇总，按 {@code TXN_DATE} 单键 GROUP BY，一次查回。
     *
     * <p>甲方 BUS 行格式只有 4 段：{@code 日期|对账金额|付款金额|优惠金额}，因此这里只有一段键。
     * 本表没有独立的实收列，对账金额与付款金额都取 {@code SUM(TOTAL_AMOUNT)}。</p>
     *
     * @return 每行含 TXN_DATE / RECON_AMOUNT / PAY_AMOUNT
     */
    List<Map<String, Object>> selectBusSummary(@Param("startDate") String startDate,
                                                @Param("endDate") String endDate,
                                                @Param("windowStart") String windowStart,
                                                @Param("windowEnd") String windowEnd);

    /**
     * DETAIL 虚拟电子多日计次票明细抽取，keyset（游标）分页，游标与限制同 {@link #selectExpPage}。
     *
     * <p>本模块只负责甲方规格里的「超时费」那一部分：多日计次票正常过闸不对账，只有产生超时费的
     * 行程要针对超时费对账、且在文件里类型为「出站」。因此筛选是
     * {@code NVL(OVERTIME_AMOUNT,0) > 0} 且卡类型属于日票/计次票四码。
     * 「发售」类型的行由 daily-ticket-server 负责，<b>NEVER 在本模块产出</b>。</p>
     *
     * @return 每行含 ID / TXN_DATE / CARD_ID / OUT_TIME / OVERTIME_AMOUNT / EXIT_STATION_NAME / DEVICE_ID
     */
    List<Map<String, Object>> selectDetailPage(@Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("windowStart") String windowStart,
                                               @Param("windowEnd") String windowEnd,
                                               @Param("lastOutTime") String lastOutTime,
                                               @Param("lastId") Long lastId,
                                               @Param("limit") int limit);
}
