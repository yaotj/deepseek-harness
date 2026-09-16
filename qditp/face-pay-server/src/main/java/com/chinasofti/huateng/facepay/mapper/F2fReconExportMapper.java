package com.chinasofti.huateng.facepay.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 日终对账抽取专用查询，只读 {@code F2F_ORDER} 与 {@code F2F_PAYMENT}，不复用任何联机链路的 mapper。
 *
 * <p>刻意独立成一个 mapper：对账抽取是批处理口径（扫时间窗口、只取成功支付、库内 GROUP BY），
 * 与联机查询的口径和索引策略完全不同，混进现有 mapper 后任何一方调过滤条件都会误伤另一方。
 * 现有 mapper / XML 一行都没改。</p>
 *
 * <p><b>本模块只产出 ITP.PAY 与 ITP.BUS 两类汇总</b>，全部方法都是聚合查询、没有明细分页。
 * ITP.EXP 与 ITP.DETAIL 与当面付无关，由 gate-txn-pay 与 daily-ticket 负责。</p>
 *
 * <p><b>与 collect-pay 那个源的两处关键差异，改动时 MUST 一并看齐：</b></p>
 * <ol>
 *   <li><b>时间列是真 {@code TIMESTAMP(6)}，不是 {@code VARCHAR2}。</b>
 *       collect-pay 的四张旧表把时间存成 {@code VARCHAR2(100)} 的 {@code yyyy-MM-dd HH:mm:ss}、
 *       因此直接做字符串比较；本模块的 {@code PAID_TMS} / {@code FINISH_TMS} 是 TIMESTAMP，
 *       窗口参数 MUST 传 {@link Timestamp}（取 {@code ReconExportReqDTO.getWindowStartTimestamp()}），
 *       <b>NEVER 把 {@code getWindowStartDashed()} 那种字符串直接比过来</b>——Oracle 会做隐式转换，
 *       格式不匹配即 {@code ORA-01843}（account-server 的 {@code reg-stats} 已因同型问题炸过一次）。</li>
 *   <li><b>金额列是 {@code NUMBER(12)}，单位分，NEVER 套 {@code TO_NUMBER}。</b>
 *       collect-pay 那边套 {@code TO_NUMBER} 是因为旧表金额列是字符串，本模块套上去纯属多余。</li>
 * </ol>
 *
 * <p><b>成功口径：{@code F2F_PAYMENT.PAY_STATUS = 'SUCCESS'} 的白名单判定。</b>
 * 该列取值 {@code INIT / PROCESSING / SUCCESS / FAILED / UNKNOWN}，其中 {@code UNKNOWN} 表示
 * 对端未明确应答、要靠回查收口，<b>NEVER 写成「非 FAILED 即成功」之类的黑名单</b>——中间态被当成
 * 已收款会让本期虚增、下期重复入账。{@code UK_F2F_PAY_SUCCESS} 保证一单最多一条成功支付，
 * 因此 join 后不会放大笔数。</p>
 *
 * <p>返回类型统一 {@code Map<String,Object>}：聚合只产出少数几列，映射成 30 余列的实体等于每行
 * 多背一堆 null 字段。<b>{@code COUNT} / {@code SUM} 的结果在 Map 里是 {@code BigDecimal}</b>，
 * 取值 MUST 经调用方的转换方法，直接强转 {@code (Long)} 会 ClassCastException。</p>
 *
 * <p><b>退款不参与对账扣减</b>：{@code F2F_REFUND} 一行都不读，与 collect-pay 源同口径
 * （甲方 PAY / BUS 规格里没有退款段，退款是另一条链路的账）。<b>NEVER 顺手减 {@code REFUND_AMOUNT}</b>
 * ——那会让同一笔在退款当日的账与购买当日的账互相抵扣、两天都对不上。</p>
 */
@Mapper
public interface F2fReconExportMapper {

    /**
     * ITP.PAY —「BOM/TVM 发售」组（段 5 / 6）：设备侧购票，{@code BIZ_TYPE='01'} 且
     * {@code CHANNEL IN ('02','03')}（02-TVM，03-BOM）。
     *
     * <p>键取 {@code TO_CHAR(PAID_TMS,'YYYYMMDD')} / 线路 / 车站 / {@code DEVICE_ID} /
     * {@code PAY_CHANNEL_CODE}；金额取 {@code F2F_PAYMENT.PAY_AMOUNT}（实付）。</p>
     *
     * @param windowStart 窗口下限（含）
     * @param windowEnd   窗口上限（不含）
     * @return 每行含 TXN_DATE / LINE_CODE / STATION_CODE / DEVICE_ID / PAY_CHANNEL_CODE
     *         / TXN_COUNT / TXN_AMOUNT
     */
    List<Map<String, Object>> selectDeviceSalePaySummary(@Param("windowStart") Timestamp windowStart,
                                                        @Param("windowEnd") Timestamp windowEnd);

    /**
     * ITP.PAY —「APP 购票」组（段 13 / 14）：{@code BIZ_TYPE='01'} 且 {@code CHANNEL='01'}。
     *
     * <p>APP 单没有受理设备，{@code DEVICE_ID} 一般为空，该段由 Java 侧按 Map 取值自然留空。</p>
     */
    List<Map<String, Object>> selectAppSalePaySummary(@Param("windowStart") Timestamp windowStart,
                                                     @Param("windowEnd") Timestamp windowEnd);

    /**
     * ITP.PAY —「BOM/TVM 充值」组（段 7 / 8）：{@code BIZ_TYPE='02'}，不分渠道。
     */
    List<Map<String, Object>> selectTopupPaySummary(@Param("windowStart") Timestamp windowStart,
                                                   @Param("windowEnd") Timestamp windowEnd);

    /**
     * ITP.PAY —「BOM 行政处理」组（段 15 / 16）：{@code BIZ_TYPE='04'} 且 {@code TRANS_TYPE='42'}。
     *
     * <p><b>这一组在 collect-pay 那个源里是恒 0 的，本源能算出来</b>：旧表 {@code TBL_BOM_ORDER_PAY}
     * 的 {@code TRANS_TYPE} 取值在仓库内三处证据互相矛盾（{@code BomNoCashOrder} 的注释清单里没有
     * 「发售」、{@code BomBusinessCodeEnum} 的 {@code 01} 与 {@code 22} 描述都写「充值」、
     * {@code BomOrderServiceImpl:950} 又把发售硬编码成 {@code 01}），所以那边只能整表归「发售」组；
     * 而 {@code F2F_ORDER} 用 {@code BIZ_TYPE} 表达业务类型、{@code TRANS_TYPE} 只表达 BOM 交易类型，
     * 两者正交，{@code 42 行政处理} 的语义在列注释里是唯一的（并有 {@code ADMIN_TRANS_TYPE} 承载
     * {@code 01~0A} 的细分）。<b>NEVER 因为「collect-pay 那边是 0」就把本组也写成 0。</b></p>
     */
    List<Map<String, Object>> selectBomAdminPaySummary(@Param("windowStart") Timestamp windowStart,
                                                      @Param("windowEnd") Timestamp windowEnd);

    /**
     * ITP.PAY —「BOM 处理」组（段 19 / 20）：{@code BIZ_TYPE='04'} 且 {@code TRANS_TYPE} 不是
     * {@code '42'}（含 NULL），即 {@code 02 超时更新 / 03 超程更新 / 04 未出站 / 05 无入站 /
     * 06 退卡退票 / 22 充值 / 2A 黑名单锁定 / 2B 锁定解除} 这些非行政处理的 BOM 非现金收款。
     *
     * <p>与上一条构成对 {@code BIZ_TYPE='04'} 的<b>完全二分</b>，因此 BOM 非现金收款不会漏账、
     * 也不会被两组重复计入。<b>改任一条的谓词 MUST 同时改另一条</b>，否则二分被破坏。</p>
     */
    List<Map<String, Object>> selectBomOtherPaySummary(@Param("windowStart") Timestamp windowStart,
                                                      @Param("windowEnd") Timestamp windowEnd);

    /**
     * ITP.BUS —— 按日汇总，键只有日期一段。
     *
     * <p>口径与 ITP.PAY 五组的并集完全一致（{@code BIZ_TYPE IN ('01','02','04')}），
     * <b>MUST 保持一致</b>：两类文件取自同一批成功支付，口径分叉会让 ACC 侧两份文件对不上。</p>
     *
     * @return 每行含 TXN_DATE / TXN_AMOUNT
     */
    List<Map<String, Object>> selectBusSummary(@Param("windowStart") Timestamp windowStart,
                                               @Param("windowEnd") Timestamp windowEnd);

    /**
     * 漏账探针一：窗口内有成功支付、但 {@code BIZ_TYPE} 不在 {@code ('01','02','04')} 的单数。
     *
     * <p>上面五条 select 的 {@code BIZ_TYPE} 谓词是<b>白名单</b>，因此新增业务类型（或
     * {@code BIZ_TYPE='03'} 取票单意外挂上了成功支付）时，那部分钱<b>不会进任何一段度量、
     * 也不会报错</b>——纯文本对账文件没有 schema，下游读不出异常。本探针把它变成一条 WARN 日志。
     * <b>NEVER 删</b>：这是本项目反复踩过的「静默漏账」类缺陷的唯一出口。</p>
     */
    Long countUncoveredPaidOrders(@Param("windowStart") Timestamp windowStart,
                                  @Param("windowEnd") Timestamp windowEnd);

    /**
     * 漏账探针二：窗口内支付成功（按 {@code F2F_PAYMENT.FINISH_TMS} 落窗）、但订单
     * {@code PAID_TMS} 为空的单数。
     *
     * <p>五条汇总 select 的窗口列与日期段都取 {@code F2F_ORDER.PAID_TMS}（窗口列与分组列 MUST 同源，
     * 混用会让同一笔在相邻两天的窗口里重复或漏掉）。{@code PAID_TMS} 由 {@code markPaid} 写入，
     * 其 WHERE 是 {@code ORDER_STATUS IN ('CREATED','PAYING')} 的状态白名单，理论上存在
     * 「支付已成功、但状态早已不在白名单里、于是 {@code PAID_TMS} 仍为空」的残缺行；
     * 这类行会被五条汇总<b>静默丢掉</b>。本探针把它变成一条 WARN 日志。</p>
     */
    Long countPaidWithoutPaidTms(@Param("windowStart") Timestamp windowStart,
                                 @Param("windowEnd") Timestamp windowEnd);
}
