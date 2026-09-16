package com.chinasofti.huateng.gatetxnpay.mapper;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.model.page.OfflineCodeStatView;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.app.TripDataDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface GateTxnPayMapper {
    /**
     * 新增过闸扣费订单。
     */
    int insert(GateTxnPay record);

    /**
     * 按交易业务键查询订单，用于闸机重复通知幂等处理。
     */
    GateTxnPay selectByBizKey(@Param("cardId") String cardId,
                              @Param("trxType") String trxType,
                              @Param("outTime") String outTime,
                              @Param("ticketTransSeq") String ticketTransSeq,
                              @Param("deviceId") String deviceId,
                              @Param("txnDate") String txnDate);

    /**
     * 待支付态推进：仅允许从 INIT / RETRY 更新为 PROCESSING / RETRY。
     *
     * <p>刻意排除 PROCESSING：已受理订单若被迟到的失败结果降级回 RETRY，
     * 会被 {@code retryPay} 当作可重试订单再次发起扣款。</p>
     */
    int updateStatusFromPending(@Param("orderNo") String orderNo,
                                @Param("txnDate") String txnDate,
                                @Param("debitStatus") String debitStatus,
                                @Param("remark") String remark);

    /**
     * 终态收敛：仅允许从 PROCESSING 更新为 SUCCESS / FAIL，防止重复回调。
     */
    int updateStatusIfProcessing(@Param("orderNo") String orderNo,
                                 @Param("txnDate") String txnDate,
                                 @Param("debitStatus") String debitStatus,
                                 @Param("remark") String remark);

    /**
     * 支付结果回调驱动的终态收敛：仅允许 INIT / PROCESSING / RETRY 更新为 SUCCESS / FAIL。
     *
     * <p>{@code updateStatusIfProcessing} 只覆盖 PROCESSING，漏掉了「同步响应失败停在 RETRY」
     * 与「异步线程未及推进停在 INIT」两种订单——这两种订单在支付平台侧仍可能扣款成功，
     * 只等回调收口，漏掉即永久停在中间态。SUCCESS / FAIL 不在白名单内，重复回调不改写终态。</p>
     */
    int convergeDebitStatus(@Param("orderNo") String orderNo,
                            @Param("txnDate") String txnDate,
                            @Param("debitStatus") String debitStatus,
                            @Param("remark") String remark);

    /**
     * 在线补款支付成功后的收敛：允许 INIT / PROCESSING / RETRY / <b>FAIL</b> 更新为 SUCCESS。
     *
     * <p>与 {@link #convergeDebitStatus} 并存、<b>白名单多一个 {@code FAIL}</b>，这是有意的：
     * 补款下单校验放行的「欠费可补」口径包含 FAIL 单，能下单就必须能收敛，
     * 否则会出现「放行下单 + 补款支付成功 + 收敛不了」——钱已实收而行程仍挂欠费。
     * 而 {@link #convergeDebitStatus} 服务的是支付结果回调，在那条链路里 FAIL 是已到达的终态、
     * 不该被回调改写。<b>两条的白名单本就该不同，NEVER 合并成一条。</b></p>
     *
     * <p>目标状态写死 {@code SUCCESS}（不做入参）：补款成功是唯一走到这里的场景。</p>
     *
     * <p>本方法是 2026-09-16 从 face-pay-server 迁入的 —— 那边原先自己持有一份同形 UPDATE，
     * 直写本模块 owner 的表。<b>NEVER 在 face-pay 侧加回任何对 {@code GATE_TXN_PAY} 的写语句。</b></p>
     *
     * @return 影响行数；<b>0 行不等于失败</b>，调用方 MUST 回查当前状态区分
     *         「已被别人收敛（SUCCESS，属重复扣款）」与「状态不在白名单内（需人工）」
     */
    int convergeDebitStatusForSupplement(@Param("orderNo") String orderNo,
                                         @Param("txnDate") String txnDate,
                                         @Param("remark") String remark);

    /**
     * 更新扣费当前状态。
     */
    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("txnDate") String txnDate,
                     @Param("debitStatus") String debitStatus,
                     @Param("remark") String remark);

    /** 运营端分页查询扣费订单，参数需与 countOperationPage 保持一致。 */
    List<GateTxnPay> selectOperationPage(@Param("orderNo") String orderNo,
                                         @Param("cardId") String cardId,
                                         @Param("thirdUserId") String thirdUserId,
                                         @Param("signChannelCode") String signChannelCode,
                                         @Param("cardType") String cardType,
                                         @Param("debitStatus") String debitStatus,
                                         @Param("startDate") String startDate,
                                         @Param("endDate") String endDate,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /** 统计运营端扣费订单分页结果总数。 */
    int countOperationPage(@Param("orderNo") String orderNo,
                           @Param("cardId") String cardId,
                           @Param("thirdUserId") String thirdUserId,
                           @Param("signChannelCode") String signChannelCode,
                           @Param("cardType") String cardType,
                           @Param("debitStatus") String debitStatus,
                           @Param("startDate") String startDate,
                           @Param("endDate") String endDate);

    /** 运营端分页展示用：按站点编码批量查询 STATION_INFO 中文站名，行映射键为 STATION_CODE / STATION_NAME。 */
    List<Map<String, Object>> selectStationNames(@Param("codes") List<String> codes);

    // ==================== 综管台离线码交易统计 ====================

    /**
     * 按车站分组统计日期窗内离线码（{@code OFFLINE_FLAG='Y'}）交易笔数与独立卡数。
     *
     * <p>车站取 {@code NVL(OUT_STATION, IN_STATION)}：离线码以出站交易落库，个别异常行
     * 可能只有进站站。{@code startDate} / {@code endDate}（{@code yyyyMMdd}）MUST 非空，
     * 是 TXN_DATE 月分区的裁剪条件。</p>
     */
    List<OfflineCodeStatView> countOfflineByStationGroup(@Param("startDate") String startDate,
                                                         @Param("endDate") String endDate);

    /**
     * 汇总日期窗内离线码交易总笔数与独立卡数（跨车站去重，因此不能对分组行求和）。
     *
     * <p>返回行的 {@code stationCode} / {@code stationName} 为空。参数约束与
     * {@link #countOfflineByStationGroup} 一致。</p>
     */
    OfflineCodeStatView countOfflineSummary(@Param("startDate") String startDate,
                                            @Param("endDate") String endDate);

    /** 退款前锁定查询指定扣费订单的可退款状态和金额。 */
    GateTxnPay selectByOrderNo(@Param("orderNo") String orderNo);

    // ==================== 综管台批量退超时罚金：圈单查询 ====================

    /**
     * 圈出日期窗内可退超时罚金的订单分页列表。
     *
     * <p>近似圈定口径（已确认）：{@code OVERTIME_AMOUNT > 0}（确含超时费）、
     * {@code ORDER_EXP_TYPE = '1'}（单边）、{@code TICKET_STATUS = '07'}（超时出站）、
     * {@code DEBIT_STATUS IN ('SUCCESS','PROCESSING')}（{@link DebitStatus#isRefundable} 白名单）、
     * {@code NVL(OUT_STATION, IN_STATION) = stationCode}。该口径是「圈出候选」，
     * 真正的可退校验（日票拒退、金额上限、状态白名单）仍由单笔 {@code requestRefund} 逐单把关，
     * 因此本查询只做粗筛，**NEVER** 用它直接判定能否退款。</p>
     *
     * <p>{@code startDate} / {@code endDate}（{@code yyyyMMdd}）MUST 非空，是 TXN_DATE 月分区的裁剪条件。</p>
     */
    List<GateTxnPay> selectOvertimeRefundablePage(@Param("stationCode") String stationCode,
                                                  @Param("startDate") String startDate,
                                                  @Param("endDate") String endDate,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    /** 统计圈单结果总数，过滤条件 MUST 与 {@link #selectOvertimeRefundablePage} 完全一致。 */
    int countOvertimeRefundable(@Param("stationCode") String stationCode,
                                @Param("startDate") String startDate,
                                @Param("endDate") String endDate);

    /** 按交易业务键查询订单（用于 ticket-server 查询 GT 订单号）。 */
    GateTxnPay selectByBizKeyForQuery(@Param("cardId") String cardId,
                                      @Param("trxType") String trxType,
                                      @Param("outTime") String outTime,
                                      @Param("ticketTransSeq") String ticketTransSeq,
                                      @Param("deviceId") String deviceId,
                                      @Param("txnDate") String txnDate);

    // ==================== IF8A-05 APP 交易记录列表 ====================

    /**
     * 分页查询进出站交易记录（用于 IF8A-05）。
     *
     * <p>返回结果按 OUT_TIME DESC, ID DESC 排序。offset/limit 为 null 时不分页。</p>
     *
     * @param debitRequestResult 扣款结果过滤：null 或空=全部，"0"=已扣款成功，"1"=未扣款成功。
     *                           口径落在 DEBIT_STATUS 上，与 {@link #countFailedOrder} 的结清判定一致。
     * @param cardTypeList       发卡卡类型列表，非空时按 {@code CARD_TYPE IN (...)} 过滤并**忽略**
     *                           {@code cardType}。APP 日票聚合码 05 会展开成 0445~0448，因此不能用单值。
     */
    List<GateTxnPay> selectTransList(@Param("thirdUserId") String thirdUserId,
                                     @Param("cardIdList") List<String> cardIdList,
                                     @Param("cardType") String cardType,
                                     @Param("cardTypeList") List<String> cardTypeList,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate,
                                     @Param("ticketCode") String ticketCode,
                                     @Param("debitRequestResult") String debitRequestResult,
                                     @Param("offset") Integer offset,
                                     @Param("limit") Integer limit);

    /**
     * 统计 IF8A-05 分页查询结果总数。
     *
     * <p>过滤条件 MUST 与 {@link #selectTransList} 完全一致，否则 totalPage 与实际列表长度会对不上。</p>
     */
    int countTransList(@Param("thirdUserId") String thirdUserId,
                       @Param("cardIdList") List<String> cardIdList,
                       @Param("cardType") String cardType,
                       @Param("cardTypeList") List<String> cardTypeList,
                       @Param("startDate") String startDate,
                       @Param("endDate") String endDate,
                       @Param("ticketCode") String ticketCode,
                       @Param("debitRequestResult") String debitRequestResult);

    // ==================== IF8A-41 APP 账单统计 ====================

    /**
     * IF8A-41 账单统计：一条 SQL 同时算出原价 / 实付 / 优惠 / 超时费。
     *
     * <p>数据源是 {@code GATE_TXN_PAY} 而非 {@code QRCODE_TXN_DETAIL}：本表同时拥有
     * {@code ORIGINAL_FARE}（地铁原价）、{@code TRX_AMOUNT}（票价）、{@code OVERTIME_AMOUNT}（超时加收）
     * 与 {@code TOTAL_AMOUNT}（实付 = 票价 + 超时费）四个量，单表即可算完，不需要跨服务合并。
     * 两表的 {@code TRX_AMOUNT} / {@code OVERTIME_AMOUNT} 逐行相等（2026-09-10 LEFT JOIN 8 行核对）。</p>
     *
     * <p>四个金额的口径与 {@code NEVER} 约束见 {@link TripDataDTO} 类注释。过滤条件 MUST 与
     * {@link #countTransList} 保持同一口径，否则统计数与列表条数会对不上。</p>
     *
     * @return 永不为 null（{@code COUNT(1)} 保证有一行），但 {@code SUM()} 在零行时返回 NULL，
     *         调用方 MUST 逐字段判空补 "0.00"。
     */
    TripDataDTO selectTransStatistics(@Param("request") RequestTransStatisticsReqDTO request);

    /**
     * 查询用户指定支付渠道在指定时间之后是否存在扣费失败订单。
     */
    int countFailedOrder(@Param("thirdUserId") String thirdUserId,
                         @Param("paymentVendor") String paymentVendor,
                         @Param("requestTime") LocalDateTime requestTime);

    /**
     * 查询单张卡是否存在未结清扣费订单（供 blacklist-server 自动解除黑名单调用）。
     *
     * <p>结清口径与 {@link #countFailedOrder} 完全一致（{@code DEBIT_STATUS} 非 SUCCESS），
     * 区别只是**不带渠道、不带时间下限**：{@code BLACKLIST} 表没有渠道字段、拉黑也不区分渠道，
     * 因此判定必须覆盖该卡的全部历史欠费。改动其中任一处的结清口径 MUST 同步另一处。</p>
     *
     * <p>该查询不带 {@code TXN_DATE}，走不到月分区裁剪，会扫全部分区。调用方是每天两次、
     * 单次只查一张卡的批处理，代价可接受；**NEVER** 把它放进过闸等联机链路。</p>
     */
    int countUnsettledOrderByCardId(@Param("cardId") String cardId);

    // ==================== IF8A-35 APP 用户账务信息 ====================

    /**
     * 一条 SQL 同时统计用户的「未支付」与「扣费失败」订单数（IF8A-35）。
     *
     * <p>分档是**白名单**：{@code unpaidCount} 只数 {@code INIT} / {@code PROCESSING}，
     * {@code failureCount} 只数 {@code FAIL} / {@code RETRY}。{@code CLOSED} 与脏数据
     * {@code NULL} 两档都不落入，因此两数之和 ≠ {@link #countFailedOrder} 的「非 SUCCESS」总数，
     * 三者口径不同，**NEVER** 拿来互相校验。</p>
     *
     * <p>两个数量合并成一次扫描，避免同一用户区间被扫两遍。</p>
     *
     * <p>{@code startDate} 是 {@code yyyyMMdd} 格式的字符串下限（如 {@code "20260608"}），
     * MUST 非空。**生产库 {@code TXN_DATE} 实际是 {@code VARCHAR2(16)} 存 {@code yyyyMMdd}，
     * 不是 {@code DATE}**（2026-09-08 实测 {@code ALL_TAB_COLUMNS}；仓库 DDL
     * {@code gate-txn-pay-schema.sql:28} 写的 {@code DATE} 与生产库不一致）。口径与
     * {@link #countTransList} 完全一致，改动前 MUST 先核对生产库列类型。</p>
     *
     * <p>踩过的两个坑，**NEVER 重犯**：传 {@link java.time.LocalDate} + {@code jdbcType=DATE}
     * 抛 {@code ORA-01843}；改成 {@code ADD_MONTHS(TRUNC(SYSDATE), ?)} 抛 {@code ORA-01861}，
     * 且后者**只在扫到实际数据行时触发**——无欠费记录的用户反而返回成功，极易误判为已修复。</p>
     *
     * <p>时间下限也是分区裁剪条件：不带它就要扫全部分区。本方法在 APP 联机链路上
     * （不同于 {@link #countUnsettledOrderByCardId} 那条批处理专用的全历史查询），
     * 全分区扫描会造成长时间阻塞的 DB 调用，在 {@code spring.threads.virtual.enabled=true}
     * 下会 pin 住载体线程。</p>
     */
    RequestUserAccInfoResult countUserAccInfo(@Param("thirdUserId") String thirdUserId,
                                             @Param("startDate") String startDate);

    // ==================== IF8A-26 补款下单 ====================

    /**
     * 按订单号列表批量查询扣费订单（用于 IF8A-26 校验待补款的原订单）。
     *
     * <p>不带 TXN_DATE 因此走不到分区裁剪，只命中 UK_GATE_TXN_PAY_ORDER_NO 的前缀列。
     * 调用方 MUST 限制列表长度：Oracle 的 IN 列表上限 1000，且列表越长扫描的分区越多。</p>
     */
    List<GateTxnPay> selectByOrderNos(@Param("orderNos") List<String> orderNos);

    // ==================== 历史 ORIGINAL_FARE 补数 ====================

    /**
     * 查询指定交易日期区间内 {@code ORIGINAL_FARE} 为空、且进出站齐全的订单。
     *
     * <p>{@code startDate} / {@code endDate}（{@code yyyyMMdd}）MUST 非空：本表按 {@code TXN_DATE}
     * 月分区，不带区间就要扫全部分区。进出站为空的行查不出票价，直接在 SQL 里排除，
     * 避免调用方为它们白跑一次 para-server 调用。</p>
     */
    List<GateTxnPay> selectMissingOriginalFare(@Param("startDate") String startDate,
                                              @Param("endDate") String endDate,
                                              @Param("limit") int limit);

    /**
     * 回填 {@code ORIGINAL_FARE}，仅当该列仍为空时生效。
     *
     * <p>{@code ORIGINAL_FARE IS NULL} 是幂等条件：并发重复调用或人工重跑都只会写第一次，
     * **NEVER** 去掉它——出站时已写好的原价快照是当时参数版本的值，回填用的是当前版本，
     * 覆盖等于篡改历史账单口径。</p>
     */
    int updateOriginalFareIfNull(@Param("orderNo") String orderNo,
                                 @Param("txnDate") String txnDate,
                                 @Param("originalFare") Integer originalFare);

    // ==================== 离线码待重算金额订单的补偿 ====================

    /**
     * 查询离线码出站时金额重算失败、等待补偿的订单。
     *
     * <p>判据是 {@code DISCOUNT_CALC_STATUS = 'OFFLINE_FARE_PENDING'} 且仍处 {@code INIT}。
     * 这类行的 {@code TOTAL_AMOUNT} 是 0 但**不是免扣费交易**，NEVER 按 0 金额收口成 SUCCESS。
     * 与 {@link #selectMissingOriginalFare} 同理 MUST 带 {@code TXN_DATE} 区间，避免扫全部月分区。</p>
     */
    List<GateTxnPay> selectOfflineFarePending(@Param("startDate") String startDate,
                                             @Param("endDate") String endDate,
                                             @Param("limit") int limit);

    /**
     * 重算成功后回写金额与优惠快照，并把 {@code DISCOUNT_CALC_STATUS} 推出 PENDING 态。
     *
     * <p>WHERE 内的 {@code DEBIT_STATUS='INIT' AND DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'}
     * 既是幂等条件也是多副本下的抢占条件：返回 1 才代表本副本拿到了这笔，返回 0 说明别的副本
     * 已重算或订单已被人工干预。调用方 **MUST** 据此决定是否继续调 pay-sign，
     * **NEVER** 忽略返回值——否则同一笔会被重复发起扣款。</p>
     */
    int updateOfflineFareRecalculated(GateTxnPay order);

    /**
     * 重算再次失败时只追加失败原因，保持 PENDING 态等下一轮。
     *
     * <p>**NEVER** 在这里改 {@code DEBIT_STATUS}：置 FAIL 会让订单进终态、补偿再也捞不到，
     * 置 SUCCESS 则是资损。</p>
     */
    int updateOfflineFarePendingMsg(@Param("orderNo") String orderNo,
                                    @Param("txnDate") String txnDate,
                                    @Param("discountCalcMsg") String discountCalcMsg);
}
