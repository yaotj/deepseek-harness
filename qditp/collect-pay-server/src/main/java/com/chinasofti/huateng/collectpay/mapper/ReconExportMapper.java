package com.chinasofti.huateng.collectpay.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 日终对账抽取专用查询，只读四张收款主表，不复用任何联机链路的 mapper。
 *
 * <p>刻意独立成一个 mapper：对账抽取是批处理口径（扫时间窗口、只取成功单、库内 GROUP BY），
 * 与联机查询的口径和索引策略完全不同，混进现有 mapper 后任何一方调过滤条件都会误伤另一方。
 * 现有 mapper / XML 一行都没改。</p>
 *
 * <p><b>本模块只产出 ITP.PAY 与 ITP.BUS 两类汇总，全部方法都是聚合查询、没有明细分页。</b>
 * 原先给 ITP.DETAIL 用的四条 keyset 明细 select 已整段删除：DETAIL 按甲方规格是「虚拟电子多日
 * 计次票文件」，与 TVM / BOM 无关，已改由 gate-txn-pay 与 daily-ticket 负责。</p>
 *
 * <p>本模块四张表的时间列全部是 {@code VARCHAR2(100)}、格式 {@code yyyy-MM-dd HH:mm:ss}
 * （写入值来自 {@code utils/DateUtils.getNowTime()}），因此窗口过滤直接做字符串比较，
 * 参数取 {@code ReconExportReqDTO.getWindowStartDashed()} / {@code getWindowEndDashed()}。
 * <b>NEVER 对时间列套 TO_DATE 之类的函数</b>——那会让索引失效。</p>
 *
 * <p><b>金额列也是 {@code VARCHAR2(100)}</b>，所以汇总 SQL 里的 SUM 一律套 {@code TO_NUMBER}，
 * 否则 Oracle 走隐式转换、遇到脏数据直接 ORA-01722，且口径不可控。</p>
 *
 * <p>返回类型统一 {@code Map<String,Object>}：聚合只产出少数几列，映射成 20 余列的实体等于每行
 * 多背一堆 null 字段。<b>COUNT / TO_NUMBER 的结果在 Map 里是 {@code BigDecimal}</b>，
 * 取值 MUST 经调用方的转换方法，直接强转 {@code (Long)} 会 ClassCastException。</p>
 *
 * <p>混合大小写列名（{@code totalPrice} / {@code merchantOrderNo} /
 * {@code PAYCENTER_channelOrderNo}）<b>一律回避不引用</b>：Oracle 里这类列必须带双引号才能命中，
 * 不带引号会 ORA-00904。</p>
 */
@Mapper
public interface ReconExportMapper {

    /**
     * ITP.PAY —「BOM/TVM 发售」组之一：TVM 扫码购票（{@code TBL_TVM_ORDER_PAY}），库内 GROUP BY。
     *
     * <p>不分页：聚合后只有几百到几千行，分页反而要把同一个聚合跑多遍。
     * <b>NEVER 改成把明细拉回 Java 再聚合</b>——那等于把全量搬进堆内存。</p>
     *
     * <p>键取 {@code REPLACE(SUBSTR(CREATE_TIME,1,10),'-','')} / {@code IN_STATION_CODE} /
     * {@code DEVICE_ID} / {@code CHANNEL}；金额取 {@code TOTAL_PRICE}。</p>
     *
     * <p>线路段来自 {@code LEFT JOIN STATION_INFO S ON S.STATION_CODE = T.IN_STATION_CODE}。
     * join 键用 {@code IN_STATION_CODE} 而不是 {@code OUT_STATION_CODE}：一是与车站段同源
     * （车站段本来就是 {@code IN_STATION_CODE}，取 OUT 会出现「车站 6 号线 + 线路 01」的矛盾行），
     * 二是 2026-09-11 测试库实测 111 行里有 55 行 {@code OUT_STATION_CODE} 为 NULL、
     * {@code IN_STATION_CODE} 一行不缺。<b>MUST 保持 LEFT JOIN</b>，维表缺记录时该组线路段为空
     * 但账仍在；INNER JOIN 会整组漏账。<b>NEVER 用车站码前 2 位推线路。</b></p>
     *
     * @param windowStart 窗口下限（含），{@code yyyy-MM-dd HH:mm:ss}
     * @param windowEnd   窗口上限（不含），{@code yyyy-MM-dd HH:mm:ss}
     * @return 每行含 TXN_DATE / LINE_CODE / IN_STATION_CODE / DEVICE_ID / CHANNEL / TXN_COUNT / TXN_AMOUNT
     */
    List<Map<String, Object>> selectTvmPayPaySummary(@Param("windowStart") String windowStart,
                                                     @Param("windowEnd") String windowEnd);

    /**
     * ITP.PAY —「BOM/TVM 充值」组：TVM 扫码充值（{@code TBL_TVM_ORDER_TOPUP}），库内 GROUP BY。
     *
     * <p>该表无车站号列，车站段由 Java 补空串；<b>没有车站码也就无从关联线路，线路段同样留空</b>
     * （待甲方补数据源或接受空值，NEVER 拿 {@code DEVICE_ID} 猜车站或线路）。
     * 金额取 {@code TRANS_AMOUNT}（本次充值金额）。</p>
     *
     * @return 每行含 TXN_DATE / DEVICE_ID / CHANNEL / TXN_COUNT / TXN_AMOUNT
     */
    List<Map<String, Object>> selectTvmTopupPaySummary(@Param("windowStart") String windowStart,
                                                       @Param("windowEnd") String windowEnd);

    /**
     * ITP.PAY —「APP 购票」组：APP 在线购票（{@code TBL_TVM_APP_ORDER}），库内 GROUP BY。
     *
     * <p>该表成功状态列叫 {@code PAY_STATUS}（不是 {@code STATUS}），且 DDL 里<b>没有</b>
     * {@code DEVICE_ID}（resultMap 有、DDL 无，属仓库已知不一致），因此不引用该列，
     * 设备编号段由 Java 补空串。支付方式取 {@code PAY_CHANNEL_CODE}，金额取 {@code PAY_AMOUNT}
     * （实付金额，DDL 与 resultMap 都有且是全大写；混合大小写的 {@code totalPrice} 不引用）。</p>
     *
     * <p>线路段来自 {@code LEFT JOIN STATION_INFO S ON S.STATION_CODE = T.IN_STATION_CODE}，
     * 与 {@link #selectTvmPayPaySummary} 同一口径（同源于车站段、MUST 保持 LEFT JOIN、
     * NEVER 用车站码前缀推线路）。本表两个车站码列都不为空，选 {@code IN_STATION_CODE}
     * 是为了与车站段同源。</p>
     *
     * @return 每行含 TXN_DATE / LINE_CODE / IN_STATION_CODE / PAY_CHANNEL_CODE / TXN_COUNT / TXN_AMOUNT
     */
    List<Map<String, Object>> selectAppOrderPaySummary(@Param("windowStart") String windowStart,
                                                       @Param("windowEnd") String windowEnd);

    /**
     * ITP.PAY —「BOM/TVM 发售」组之二：BOM 非现金收款（{@code TBL_BOM_ORDER_PAY}），库内 GROUP BY。
     *
     * <p><b>整表计入「发售」组，不按 {@code TRANS_TYPE} 拆分购票 / 充值——这是有意的降级决策。</b>
     * 核对过三处仓库内证据，取值互相矛盾、无法可靠区分：</p>
     * <ul>
     *   <li>{@code entity/BomNoCashOrder.java:19~31} 的字段注释列了 {@code 02} 超时更新、
     *       {@code 03} 超程更新、{@code 04} 未出站更新、{@code 05} 无入站更新、{@code 06} 退卡退票、
     *       {@code 22} 充值、{@code 2A} 黑名单锁定、{@code 2B} 锁定解除、{@code 42} 行政处理
     *       ——<b>整份清单里没有「购票 / 发售」这一项</b>；</li>
     *   <li>{@code constant/BomBusinessCodeEnum.java:4~5} 是 {@code SALE("01","充值")} 与
     *       {@code TOPUP("22","充值")}，两个枚举的中文描述都写成「充值」，{@code 01} 到底是
     *       发售还是充值无法判定；</li>
     *   <li>{@code service/impl/BomOrderServiceImpl.java:950} 的 BOM 发售建单硬编码
     *       {@code order.setTransType("01")}，与上面那份不含发售的注释清单直接冲突。</li>
     * </ul>
     * <p>另外 {@code TRANS_TYPE} 的值有一条来源是设备上送
     * （{@code BomOrderServiceImpl.java:931} 取自请求体），生产库里必然还存在
     * {@code 02/03/04/05/06/2A/2B/42} 这些既不是购票也不是充值的行。<b>凭猜写取值会把账挂到错误
     * 分组上，宁可整表归一组并标注</b>——发售组金额偏大是可解释的口径问题，错拆是错账。
     * 甲方给出明确判据后再拆，届时 MUST 同步改本方法与 {@code ReconExportService}。</p>
     *
     * <p>金额列名是 {@code TRANS_AOUNT}（DDL 原文就少一个 M，
     * <b>NEVER 顺手改成 TRANS_AMOUNT</b>，改了就是 ORA-00904）。该表无车站号列，
     * <b>因此车站段与线路段都只能留空</b>（待甲方补数据源或接受空值，
     * NEVER 拿 {@code DEVICE_ID} 猜车站或线路）。</p>
     *
     * @return 每行含 TXN_DATE / DEVICE_ID / CHANNEL / TXN_COUNT / TXN_AMOUNT
     */
    List<Map<String, Object>> selectBomPayPaySummary(@Param("windowStart") String windowStart,
                                                     @Param("windowEnd") String windowEnd);

    /**
     * ITP.BUS —— TVM 扫码购票按日汇总（{@code TBL_TVM_ORDER_PAY}）。
     *
     * <p>BUS 只有 4 段：日期 + 对账金额 + 付款金额 + 优惠金额，<b>键只有日期一段</b>，
     * 因此这里只按日期分组，不带车站 / 设备 / 票种。</p>
     *
     * @return 每行含 TXN_DATE / TXN_AMOUNT
     */
    List<Map<String, Object>> selectTvmPayBusSummary(@Param("windowStart") String windowStart,
                                                     @Param("windowEnd") String windowEnd);

    /**
     * ITP.BUS —— TVM 扫码充值按日汇总（{@code TBL_TVM_ORDER_TOPUP}）。
     *
     * @return 每行含 TXN_DATE / TXN_AMOUNT
     */
    List<Map<String, Object>> selectTvmTopupBusSummary(@Param("windowStart") String windowStart,
                                                       @Param("windowEnd") String windowEnd);

    /**
     * ITP.BUS —— APP 在线购票按日汇总（{@code TBL_TVM_APP_ORDER}），状态列是 {@code PAY_STATUS}。
     *
     * @return 每行含 TXN_DATE / TXN_AMOUNT
     */
    List<Map<String, Object>> selectAppOrderBusSummary(@Param("windowStart") String windowStart,
                                                       @Param("windowEnd") String windowEnd);

    /**
     * ITP.BUS —— BOM 非现金收款按日汇总（{@code TBL_BOM_ORDER_PAY}），金额列 {@code TRANS_AOUNT}。
     *
     * @return 每行含 TXN_DATE / TXN_AMOUNT
     */
    List<Map<String, Object>> selectBomPayBusSummary(@Param("windowStart") String windowStart,
                                                     @Param("windowEnd") String windowEnd);
}
