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
    /** 新增过闸扣费订单。 */
    int insert(GateTxnPay record);

    /** 按交易业务键查询订单，用于闸机重复通知幂等处理。 */
    GateTxnPay selectByBizKey(@Param("cardId") String cardId,
                              @Param("trxType") String trxType,
                              @Param("outTime") String outTime,
                              @Param("ticketTransSeq") String ticketTransSeq,
                              @Param("deviceId") String deviceId,
                              @Param("txnDate") String txnDate);

    /** 待支付态推进：仅允许从 INIT / RETRY 更新为 PROCESSING / RETRY。 */
    int updateStatusFromPending(@Param("orderNo") String orderNo,
                                @Param("txnDate") String txnDate,
                                @Param("debitStatus") String debitStatus,
                                @Param("remark") String remark);

    /** 终态收敛：仅允许从 PROCESSING 更新为 SUCCESS / FAIL，防止重复回调。 */
    int updateStatusIfProcessing(@Param("orderNo") String orderNo,
                                 @Param("txnDate") String txnDate,
                                 @Param("debitStatus") String debitStatus,
                                 @Param("remark") String remark);

    /** 支付结果回调驱动的终态收敛：仅允许 INIT / PROCESSING / RETRY 更新为 SUCCESS / FAIL。 */
    int convergeDebitStatus(@Param("orderNo") String orderNo,
                            @Param("txnDate") String txnDate,
                            @Param("debitStatus") String debitStatus,
                            @Param("remark") String remark);

    /**
     * 在线补款支付成功后的收敛：允许 INIT / PROCESSING / RETRY / FAIL 更新为 SUCCESS。
     *
     * @return 影响行数，0 行不等于失败。
     */
    int convergeDebitStatusForSupplement(@Param("orderNo") String orderNo,
                                         @Param("txnDate") String txnDate,
                                         @Param("remark") String remark);

    /** 更新扣费当前状态。 */
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

    /** 按车站分组统计日期窗内离线码（{@code OFFLINE_FLAG='Y'}）交易笔数与独立卡数。 */
    List<OfflineCodeStatView> countOfflineByStationGroup(@Param("startDate") String startDate,
                                                         @Param("endDate") String endDate);

    /** 汇总日期窗内离线码交易总笔数与独立卡数（跨车站去重，因此不能对分组行求和）。 */
    OfflineCodeStatView countOfflineSummary(@Param("startDate") String startDate,
                                            @Param("endDate") String endDate);

    /** 退款前锁定查询指定扣费订单的可退款状态和金额。 */
    GateTxnPay selectByOrderNo(@Param("orderNo") String orderNo);

    /** 圈出日期窗内可退超时罚金的订单分页列表。 */
    List<GateTxnPay> selectOvertimeRefundablePage(@Param("stationCode") String stationCode,
                                                  @Param("startDate") String startDate,
                                                  @Param("endDate") String endDate,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    /** 统计圈单结果总数。 */
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

    /**
     * 分页查询进出站交易记录（用于 IF8A-05）。
     *
     * @param debitRequestResult 扣款结果过滤：null 或空=全部，"0"=已扣款成功，"1"=未扣款成功。
     * @param cardTypeList 发卡卡类型列表，非空时按 {@code CARD_TYPE IN (...)} 过滤并忽略。
     * @param issueChannelCode 发行渠道过滤：null 或空=全渠道，{@code 07}=支付宝出行。
     */
    List<GateTxnPay> selectTransList(@Param("thirdUserId") String thirdUserId,
                                     @Param("cardIdList") List<String> cardIdList,
                                     @Param("cardType") String cardType,
                                     @Param("cardTypeList") List<String> cardTypeList,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate,
                                     @Param("ticketCode") String ticketCode,
                                     @Param("debitRequestResult") String debitRequestResult,
                                     @Param("issueChannelCode") String issueChannelCode,
                                     @Param("offset") Integer offset,
                                     @Param("limit") Integer limit);

    /** 统计 IF8A-05 分页查询结果总数。 */
    int countTransList(@Param("thirdUserId") String thirdUserId,
                       @Param("cardIdList") List<String> cardIdList,
                       @Param("cardType") String cardType,
                       @Param("cardTypeList") List<String> cardTypeList,
                       @Param("startDate") String startDate,
                       @Param("endDate") String endDate,
                       @Param("ticketCode") String ticketCode,
                       @Param("debitRequestResult") String debitRequestResult,
                       @Param("issueChannelCode") String issueChannelCode);

    /**
     * IF8A-41 账单统计：一条 SQL 同时算出原价 / 实付 / 优惠 / 超时费。
     *
     * @return 永不为 null（{@code COUNT(1)} 保证有一行），但 {@code SUM()} 在零行时返回 NULL。
     */
    TripDataDTO selectTransStatistics(@Param("request") RequestTransStatisticsReqDTO request);

    /** 查询用户指定支付渠道在指定时间之后是否存在扣费失败订单。 */
    int countFailedOrder(@Param("thirdUserId") String thirdUserId,
                         @Param("paymentVendor") String paymentVendor,
                         @Param("requestTime") LocalDateTime requestTime);

    /** 查询单张卡是否存在未结清扣费订单（供 blacklist-server 自动解除黑名单调用）。 */
    int countUnsettledOrderByCardId(@Param("cardId") String cardId);

    /** 一条 SQL 同时统计用户的「未支付」与「扣费失败」订单数（IF8A-35）。 */
    RequestUserAccInfoResult countUserAccInfo(@Param("thirdUserId") String thirdUserId,
                                             @Param("startDate") String startDate);

    /** 按订单号列表批量查询扣费订单（用于 IF8A-26 校验待补款的原订单）。 */
    List<GateTxnPay> selectByOrderNos(@Param("orderNos") List<String> orderNos);

    /** 查询指定交易日期区间内 {@code ORIGINAL_FARE} 为空、且进出站齐全的订单。 */
    List<GateTxnPay> selectMissingOriginalFare(@Param("startDate") String startDate,
                                              @Param("endDate") String endDate,
                                              @Param("limit") int limit);

    /** 回填 {@code ORIGINAL_FARE}，仅当该列仍为空时生效。 */
    int updateOriginalFareIfNull(@Param("orderNo") String orderNo,
                                 @Param("txnDate") String txnDate,
                                 @Param("originalFare") Integer originalFare);

    /** 查询离线码出站时金额重算失败、等待补偿的订单。 */
    List<GateTxnPay> selectOfflineFarePending(@Param("startDate") String startDate,
                                             @Param("endDate") String endDate,
                                             @Param("limit") int limit);

    /** 重算成功后回写金额与优惠快照，并把 {@code DISCOUNT_CALC_STATUS} 推出 PENDING 态。 */
    int updateOfflineFareRecalculated(GateTxnPay order);

    /** 重算再次失败时只追加失败原因，保持 PENDING 态等下一轮。 */
    int updateOfflineFarePendingMsg(@Param("orderNo") String orderNo,
                                    @Param("txnDate") String txnDate,
                                    @Param("discountCalcMsg") String discountCalcMsg);

    /**
     * 扫出「每日批量扣费重试」的候选：{@code DEBIT_STATUS IN ('RETRY','FAIL')}、账期在窗口内、
     * 重试次数未达上限、且已过退避时刻。走现有索引 {@code IDX_GATE_TXN_PAY_STATUS_DATE}。
     *
     * @param alipayChannel {@code true} 只捞支付宝出行（{@code ISSUE_CHANNEL_CODE='07'}），
     *                      {@code false} 捞其余全部（含该列为空的历史行）。两条批量重试任务靠这个参数分流，
     *                      因为两类单子的扣费出口完全不同（见 {@code PaySignInitiator#converge}）。
     *                      两支谓词互补且覆盖全集，**NEVER 把 false 那支写成 {@code = '01'}**，
     *                      否则空值与未知渠道值的单子两边都扫不到、永远没人重试。
     * @param maxTimes 重试次数上限，达到即不再被捞出（人工用 {@code DEBIT_RETRY_TIMES >= maxTimes} 找这批）
     */
    List<GateTxnPay> selectBatchRetryCandidates(@Param("alipayChannel") boolean alipayChannel,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("maxTimes") int maxTimes,
                                               @Param("limit") int limit);

    /**
     * 抢占一笔待批量重试的订单：把 {@code FAIL / RETRY} 统一 CAS 成 {@code RETRY}、次数 +1、下次重试时刻后移。
     *
     * <p>这一条同时承担三件事，**NEVER 拆开**：①并发/重入下的唯一抢占（返回 1 才算抢到）；
     * ②把终态 {@code FAIL} 归一成 {@code RETRY}，否则后续 {@code updateStatusFromPending} 的
     * CAS 白名单（只认 INIT / RETRY）会全部落 0 行、扣费结果无处回写；③记账，防止同日重复触发连扣。
     *
     * @return 1 抢到，0 未抢到（已被别人处理、次数已达上限或退避未到）
     */
    int prepareBatchRetry(@Param("orderNo") String orderNo,
                          @Param("txnDate") String txnDate,
                          @Param("maxTimes") int maxTimes,
                          @Param("backoffMinutes") int backoffMinutes,
                          @Param("failMsg") String failMsg);

    /**
     * 扫出「近 N 分钟未扣费」的候选（`sys_job` 345 补站扣费周期查询更新，2026-09-21 编号先后为 135、265、235、345）：
     * {@code DEBIT_STATUS IN ('INIT','RETRY','FAIL')}、{@code CREATE_TIME} 落在
     * {@code [now - windowMinutes, now - minAgeSeconds]} 这个**左右都闭**的窗口内。
     *
     * <p>与 {@link #selectBatchRetryCandidates} 的三处差别，**改一处 MUST 回头看另一条**：
     * <ul>
     *   <li><b>不分渠道</b> —— 业主选择「全量未扣费单的 10 分钟快速轮」，与按渠道拆开的那两条并行；</li>
     *   <li><b>多捞 {@code INIT}</b> —— 本任务的核心场景正是「落单后扣费压根没发起」
     *       （`GateFarePaymentOrchestrator` 的 RPC 异常只记日志、不改状态）；那两条日跑任务捞不到这种；</li>
     *   <li><b>按 {@code CREATE_TIME} 而不是 {@code TXN_DATE} 收窄</b> —— 窗口只有分钟级，
     *       用 8 位日期字符串收不出来。</li>
     * </ul>
     *
     * <p>两条谓词 **NEVER 删**：
     * ①{@code minAgeSeconds} 下界 —— 刚落库几秒的单可能**正在**走扣费 RPC，
     * 立刻再发一笔就是重复扣款；②排除 {@code DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'} ——
     * 那是离线码待重算态，金额还没算准（见 {@link #selectOfflineFarePending}），
     * 按当前金额扣下去等于扣错钱。
     *
     * @param maxTimes 与那两条日跑任务**共用**的次数上限：本任务只读不写 {@code DEBIT_RETRY_TIMES}，
     *                 带上它只为「已被日跑任务耗尽次数的死单不再碰」，NEVER 去掉
     */
    List<GateTxnPay> selectRecentUnpaidCandidates(@Param("windowMinutes") int windowMinutes,
                                                 @Param("minAgeSeconds") int minAgeSeconds,
                                                 @Param("maxTimes") int maxTimes,
                                                 @Param("limit") int limit);

    /**
     * 抢占一笔近 N 分钟未扣费的订单：把 {@code INIT / FAIL} 统一 CAS 成 {@code RETRY}。
     *
     * <p>与 {@link #prepareBatchRetry} 的唯一差别是**不碰那两个记账列**
     * （{@code DEBIT_RETRY_TIMES} 不 +1、{@code DEBIT_NEXT_RETRY_TIME} 不后移）：本任务每分钟一轮、
     * 靠 10 分钟窗口自然收敛（业主裁决，见 ADR-D154），若在这里记账会把 `sys_job` 220 / 255
     * 的次数预算在 10 分钟内烧光、那两条日跑任务从此再也捞不到这批单。**NEVER 在这条语句里加回记账。**
     *
     * <p>归一成 {@code RETRY} 本身是必须的：后续 {@code updateStatusFromPending} 的 CAS 白名单
     * 只认 {@code INIT / RETRY}，{@code FAIL} 不归一就会让扣费结果无处回写。
     *
     * @return 1 抢到，0 未抢到（已被别人推进、次数已达上限、退避未到或已转成离线码待重算态）
     */
    int prepareRecentRetry(@Param("orderNo") String orderNo,
                           @Param("txnDate") String txnDate,
                           @Param("maxTimes") int maxTimes,
                           @Param("failMsg") String failMsg);

    /** 批量重试落点不是 PROCESSING 时记下语义码，供人工按 {@code DEBIT_FAIL_CODE} 归类。 */
    int updateDebitFailInfo(@Param("orderNo") String orderNo,
                            @Param("txnDate") String txnDate,
                            @Param("failCode") String failCode,
                            @Param("failMsg") String failMsg);

    /**
     * 用户主动重试扣费：扫出指定 thirdUserId（可选按 cardIdList 收窄）下
     * {@code DEBIT_STATUS IN ('INIT','RETRY','FAIL')}、且非离线码待重算态的待重扣候选。
     *
     * <p>与 {@link #selectBatchRetryCandidates} / {@link #selectRecentUnpaidCandidates} 的<b>关键差别</b>：
     * <b>不卡 {@code DEBIT_RETRY_TIMES} 上限、不卡 {@code DEBIT_NEXT_RETRY_TIME} 退避</b>——
     * 业主裁决用户显式触发即全量重扣，覆盖已达上限的死单。其余两道闸照旧：
     * ①排除 {@code DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'}（防错额扣款）；
     * ②只认 INIT/RETRY/FAIL 三态（PROCESSING 正在扣费中的单不碰，防并发双扣）。
     *
     * @param cardIdList 为空列表表示不限卡号、只按 thirdUserId 范围
     */
    List<GateTxnPay> selectUserRetryCandidates(@Param("thirdUserId") String thirdUserId,
                                              @Param("cardIdList") List<String> cardIdList,
                                              @Param("limit") int limit);

    /**
     * 用户主动重试扣费的抢占 CAS：把单归一成 {@code RETRY}、清空失败码与原因，**不碰记账列**
     * （不 +1 {@code DEBIT_RETRY_TIMES}、不后移 {@code DEBIT_NEXT_RETRY_TIME}）。
     *
     * <p>与 {@link #prepareRecentRetry} 的唯一差别是<b>去掉了 {@code DEBIT_RETRY_TIMES < maxTimes} 这道闸</b>
     * （用户主动触发即允许对死单再试，见 {@code UserDebitRetryService}）；其余完全一致：
     * 归一成 RETRY 是必须的（后续 {@code updateStatusFromPending} 的 CAS 白名单只认 INIT/RETRY），
     * 排除 OFFLINE_FARE_PENDING 是必须的（防错额扣款）。
     *
     * @return 1 抢到，0 未抢到（已被别人推进 / 已不在三态内 / 已转成离线码待重算态）
     */
    int prepareUserRetry(@Param("orderNo") String orderNo,
                         @Param("txnDate") String txnDate,
                         @Param("failMsg") String failMsg);
}
