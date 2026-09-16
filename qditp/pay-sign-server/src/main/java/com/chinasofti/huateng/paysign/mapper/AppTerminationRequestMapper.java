package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppTerminationRequestMapper {
    int insert(AppTerminationRequest record);

    AppTerminationRequest selectByRequestSignSeq(@Param("requestSignSeq") String requestSignSeq);

    /**
     * 查询用户是否存在待处理解约申请
     * 查询条件：TERMINATION_STATUS IN ('PENDING', 'SCANNING')
     */
    AppTerminationRequest selectPendingByUserId(@Param("thirdUserId") String thirdUserId);

    /**
     * 按状态分页取一批解约申请，按 REQUEST_TIME 升序（先申请先处理）。
     * 供解约处理任务扫表使用，调用方可反复调用直到返回空集。
     */
    List<AppTerminationRequest> selectByStatusLimit(@Param("status") String status,
                                                    @Param("limit") int limit);

    /**
     * 按状态 + 申请时间上限取一批解约申请，按 REQUEST_TIME 升序（先申请先处理）。
     *
     * <p>与 {@link #selectByStatusLimit} 的唯一区别是多一个 {@code REQUEST_TIME <= cutoff} 条件，
     * 用于表达「解约申请满 N 天才确认」的业务口径：cutoff 由调用方按
     * 「基准时间 - 规定天数」算出。口径是「截止点之前仍未成功的都在扫描范围内」，
     * 因此 PENDING 与 SCANNING 都走同一个 cutoff。</p>
     *
     * @param cutoff 申请时间截止点，只取该时刻及之前提交的申请
     */
    List<AppTerminationRequest> selectByStatusBefore(@Param("status") String status,
                                                     @Param("cutoff") LocalDateTime cutoff,
                                                     @Param("limit") int limit);

    /**
     * 取一批需要补偿通知的解约申请，按 NVL(NOTIFY_TIME, CREATE_TIME) 升序（最久没成功的先补），
     * 并过滤已超过最大重试次数的记录。
     * <p>命中 {@code NOTIFY_STATUS='FAILED'}，以及滞留超过 {@code staleMinutes} 分钟的
     * {@code NOTIFY_STATUS='PENDING'} —— PENDING 是流水插入时的初值，若通知结果回写自身失败，
     * 这行会永久停在 PENDING，只扫 FAILED 时补偿完全看不到它。
     * 供解约通知补偿接口扫表使用。
     */
    List<AppTerminationRequest> selectCompensableNotify(@Param("maxRetryCount") int maxRetryCount,
                                                        @Param("staleMinutes") int staleMinutes,
                                                        @Param("limit") int limit);

    int updateStatus(@Param("requestSignSeq") String requestSignSeq,
                     @Param("status") String status);

    int updateScanTime(@Param("requestSignSeq") String requestSignSeq,
                       @Param("scanTime") LocalDateTime scanTime);

    /**
     * PENDING → SCANNING 抢占：一条语句同时落状态与扫描时间，WHERE 带
     * {@code TERMINATION_STATUS = 'PENDING'} 是状态机白名单 + CAS。
     *
     * <p>{@code /internal/termination/execute} 不带事务（事务内 NEVER 调 RPC），因此
     * 「判定 PENDING」与「置 SCANNING」不能再靠事务串起来，**MUST** 用本方法一次性抢占：
     * 返回 1 才是本次调用拿到了执行权，返回 0 说明并发下已被批处理或另一次调用改走，
     * 调用方 **MUST** 检查返回值，**NEVER** 无条件继续去调支付中心——否则同一笔会重复发解约。</p>
     *
     * @return 实际更新行数，0 表示未命中 PENDING（未抢到执行权）
     */
    int markScanning(@Param("requestSignSeq") String requestSignSeq,
                     @Param("scanTime") LocalDateTime scanTime);

    /**
     * SCANNING → PENDING 回退：支付平台**明确**答复失败时把执行权交还扫表任务重试。
     *
     * <p>WHERE 带 {@code TERMINATION_STATUS = 'SCANNING'} 是 CAS，避免覆盖回调刚收口的 SUCCESS。</p>
     *
     * <p>**NEVER** 在「结果未知」（超时 / 连接异常 / 解析失败）时调用本方法：那种情况支付平台
     * 可能已受理，退回 PENDING 会让扫表任务再发一次解约。结果未知 **MUST** 留在 SCANNING，
     * 由 {@code /internal/termination/process} 的 SCANNING 分支主动查询协议状态收口。</p>
     *
     * @return 实际更新行数，0 表示已被别人改走
     */
    int revertScanningToPending(@Param("requestSignSeq") String requestSignSeq);

    /**
     * PENDING → FAILED 拒绝：置 {@code FAILED} + 失败原因 + 完成时间 + 通知待发，一条语句原子完成。
     *
     * <p>WHERE 带 {@code TERMINATION_STATUS = 'PENDING'} 是状态机白名单 + CAS。用于「存在未结清扣费订单，
     * 拒绝解约」这一路径：调用方判定 PENDING 与写 FAILED 之间隔着一次 RPC（查欠费），期间这条可能已被
     * {@code /internal/termination/execute} 抢走置成 SCANNING，甚至已被回调收口成 SUCCESS。
     * **NEVER** 拆成 {@code updateFailReason} + {@code updateCompleteTime} + {@code updateNotifyStatus}
     * 三条无 CAS 的语句——那会把 SCANNING 期间在途的解约、或已收口的 SUCCESS 直接覆盖成 FAILED，
     * 并给 APP 发一条与支付平台实际状态相反的解约失败通知。调用方 **MUST** 检查返回值。
     *
     * @return 实际更新行数，0 表示未命中 PENDING（已被别人接手）
     */
    int rejectPending(@Param("requestSignSeq") String requestSignSeq,
                      @Param("failReason") String failReason,
                      @Param("completeTime") LocalDateTime completeTime);

    /**
     * SCANNING → SUCCESS 收口：置 {@code SUCCESS} + 完成时间 + 通知待发，一条语句原子完成。
     *
     * <p>WHERE 带 {@code TERMINATION_STATUS = 'SCANNING'} 是状态机白名单 + CAS。用于解约结果回调
     * 的成功分支。**NEVER** 退回 {@code updateStatus} + {@code updateCompleteTime} +
     * {@code updateNotifyStatus} 三条无 CAS 语句（2026-09-12 之前就是那样）：
     * {@code updateStatus} 的 WHERE 只有 {@code REQUEST_SIGN_SEQ}，一条已被 {@code expireScanning}
     * 打成 {@code FAILED}（滞留超 24 小时，日级调度下必然走到）的申请会被迟到的成功回调无条件改成
     * {@code SUCCESS}，而 APP 已经收到过「解约失败」通知，随后又收到「解约成功」。
     * 调用方 **MUST** 检查返回值，0 行走 {@code TerminationStatusTransition} 判幂等还是冲突。
     *
     * <p>{@code NOTIFY_RETRY_COUNT} 归零、{@code NOTIFY_TIME} / {@code NOTIFY_RESULT} 清空：
     * 这是一条新的、还没发过的解约成功通知，口径与 {@code rejectPending} / {@code expireScanning} 一致。
     * 沿用残留轮次会让它一上来就接近 {@code app.notify.max-retry-count} 上限、补偿随即扫不到。
     *
     * @return 实际更新行数，0 表示未命中 SCANNING
     */
    int markSuccess(@Param("requestSignSeq") String requestSignSeq,
                    @Param("completeTime") LocalDateTime completeTime);

    /**
     * SCANNING → FAILED 拒绝：支付平台**明确**答复解约失败时收口，置 {@code FAILED} + 失败原因 +
     * 完成时间 + 通知待发，一条语句原子完成。
     *
     * <p>与 {@link #expireScanning} 的差别只在语义（对方答复失败 vs 我方等超时），SQL 形态相同，
     * 因此两者<b>刻意不合并</b>：{@code FAIL_REASON} 的来源与运维处置口径不同，合并后无法从表里
     * 区分「支付平台说失败」和「我方查不动」。
     *
     * <p>WHERE 带 {@code TERMINATION_STATUS = 'SCANNING'} 是 CAS。**NEVER** 退回
     * {@code updateFailReason} + {@code updateCompleteTime} + {@code updateNotifyStatus}
     * 三条无 CAS 语句 —— {@code updateFailReason} 能把已收口的 {@code SUCCESS} 覆盖成
     * {@code FAILED}，而那时通道其实已经清干净了。调用方 **MUST** 检查返回值。
     *
     * @return 实际更新行数，0 表示未命中 SCANNING
     */
    int rejectScanning(@Param("requestSignSeq") String requestSignSeq,
                       @Param("failReason") String failReason,
                       @Param("completeTime") LocalDateTime completeTime);

    /**
     * 只取 {@code TERMINATION_STATUS} 一列，供 CAS 返 0 行后回查真实状态。
     *
     * <p>**MUST** 只在 CAS 影响 0 行时调用（见 {@code TerminationStatusTransition} 的约束），
     * 命中就回查等于给每次成功迁移加一次多余的 DB 往返。
     *
     * @return 库内当前状态，行不存在时返回 {@code null}
     */
    String selectTerminationStatusBySeq(@Param("requestSignSeq") String requestSignSeq);

    /**
     * {@code markSuccess} 撞上 {@code FAILED} 时，把「需人工核对」落进 {@code FAIL_REASON}，
     * 让这一行能被 SQL 找到。
     *
     * <p>背景：支付平台侧协议其实已注销、本地签约记录已删、账户域通道已清理，唯独
     * {@code TERMINATION_STATUS} 被并发的 {@code expireScanning} 抢先打成了 {@code FAILED}，
     * 而 APP 已经收到失败通知。此时 {@code FAILED -> SUCCESS} 已被明确禁止
     * （见 {@code TerminationStatus} 的白名单与其中记录的裁决），所以**能自动做的只有「留痕」**。
     * 在此之前这条路径只有一行 ERROR 日志，日志滚掉即彻底失联。
     *
     * <p>本方法 **NEVER** 改 {@code TERMINATION_STATUS}、**NEVER** 动 {@code NOTIFY_*}：
     * 复位通知等于替业务决定「要不要给 APP 反悔」，那是业务裁决，不是并发处置。
     *
     * <p>WHERE 三段缺一不可：主键定位、{@code TERMINATION_STATUS = 'FAILED'} 的 CAS
     * （只有这一种组合需要人工；观察到别的状态就影响 0 行，调用方只记日志）、
     * 以及 {@code INSTR} 幂等闸门（支付中心会重推同一笔回调，缺它则标记被反复前置拼接，
     * 最终挤满 512 字符的 {@code FAIL_REASON} 并把原始原因截掉）。
     *
     * <p>与 {@code markChannelSyncManual} 是同一套口径：ADR-D8 有意不建工单表，
     * 人工件靠本表的列过滤。
     *
     * <p><b>{@code FAIL_REASON} 会流向 APP 通知</b>，因此写入格式与剥离逻辑一律走
     * {@code TerminationFailReason}，**NEVER 在调用点手拼字符串**（这正是 2026-09-12 那次
     * 「运维文案发给终端用户」缺陷的成因）。
     *
     * @param manualNote 前置到 {@code FAIL_REASON} 的内部说明，**MUST** 取自
     *                   {@code TerminationFailReason.successConflictNote()}
     * @param manualMark 幂等判据，**MUST** 传 {@code TerminationFailReason.MANUAL_REVIEW_MARK}
     * @return 实际更新行数；0 表示未命中 FAILED 或本行已标记过，两种都 **NEVER** 当成失败
     */
    int markConflictForManualReview(@Param("requestSignSeq") String requestSignSeq,
                                    @Param("manualNote") String manualNote,
                                    @Param("manualMark") String manualMark);

    /**
     * {@code rejectScanning} 撞上 {@code SUCCESS} 时留痕：本次回调说解约失败，而库内已收口成成功。
     *
     * <p>与 {@link #markConflictForManualReview} 形态相同、**CAS 前置状态相反**，刻意分成两条语句
     * 而不是把状态做成绑定参数：状态字面量留在 SQL 里才能被离线渲染断言钉住，而两种矛盾的人工处置
     * 口径也不同（一边是「库里失败、其实已解约」，另一边是「两条回调结论相反」）。
     *
     * <p>这一条**不会**泄漏给 APP：`SUCCESS` 行走的是成功通知分支，
     * {@code doNotifyTerminationResult} 把 {@code terminationResultMsg} 恒置空串、根本不读
     * {@code FAIL_REASON}。即便如此，写入格式仍 **MUST** 走 {@code TerminationFailReason}
     * —— 依赖「当前某个分支恰好不读这一列」是脆的。
     *
     * @param manualNote **MUST** 取自 {@code TerminationFailReason.failureConflictNote()}
     * @return 实际更新行数；0 表示未命中 SUCCESS 或已标记过
     */
    int markFailureConflictForManualReview(@Param("requestSignSeq") String requestSignSeq,
                                           @Param("manualNote") String manualNote,
                                           @Param("manualMark") String manualMark);

    int updateCompleteTime(@Param("requestSignSeq") String requestSignSeq,
                           @Param("completeTime") LocalDateTime completeTime);

    int updateFailReason(@Param("requestSignSeq") String requestSignSeq,
                         @Param("status") String status,
                         @Param("failReason") String failReason);

    int updateNotifyStatus(@Param("requestSignSeq") String requestSignSeq,
                           @Param("notifyStatus") String notifyStatus,
                           @Param("notifyTime") LocalDateTime notifyTime,
                           @Param("notifyResult") String notifyResult);

    int increaseNotifyRetryCount(@Param("requestSignSeq") String requestSignSeq);

    /**
     * 把 {@code FAILED} 的解约申请复活成 {@code PENDING}，交给扫表任务重跑。
     *
     * <p>{@code REQUEST_SIGN_SEQ} 上有唯一索引 {@code UK_ATR_REQUEST_SIGN_SEQ}，同一签约流水只能有一行，
     * 因此「重新申请解约」只能走本方法，**NEVER 再 insert**——必抛 {@code DuplicateKeyException}，
     * 被 {@code requestTermination} 外层 catch 成 {@code SYSTEM_ERROR}，用户从此再也解不了约。
     *
     * <p>SQL 的 WHERE 带 {@code TERMINATION_STATUS = 'FAILED'}，是状态机白名单 + CAS。
     * 调用方 **MUST** 检查返回值：返回 0 表示并发下状态已被改走，本次不能放行。
     *
     * @return 实际更新行数，0 表示未命中 FAILED
     */
    int reactivateFailed(@Param("requestSignSeq") String requestSignSeq,
                         @Param("cardId") String cardId,
                         @Param("cardType") String cardType,
                         @Param("paymentVendor") String paymentVendor,
                         @Param("requestTime") LocalDateTime requestTime);

    /**
     * SCANNING 收口超时：置 {@code FAILED} + 失败原因 + 完成时间 + 通知待发，一条语句原子完成。
     *
     * <p>WHERE 带 {@code TERMINATION_STATUS = 'SCANNING'} 是 CAS。SCANNING 是长期在途状态，
     * 解约回调随时可能把它收口成 {@code SUCCESS}，**NEVER** 拆成多条 update——会把已收口的
     * SUCCESS 覆盖成 FAILED。调用方 **MUST** 检查返回值，返回 0 说明这条已被别人收口。
     *
     * @return 实际更新行数，0 表示未命中 SCANNING
     */
    int expireScanning(@Param("requestSignSeq") String requestSignSeq,
                       @Param("failReason") String failReason,
                       @Param("completeTime") LocalDateTime completeTime);

    /**
     * 本地事务收口时把「解约成功后清理账户域支付通道」置为待投递（ADR-D8 第一处）。
     *
     * <p>WHERE 带 {@code TERMINATION_STATUS = 'SUCCESS'} 是 CAS，**MUST 在同一个本地事务里、
     * 紧跟 {@code updateStatus(SUCCESS)} 之后调用**。返回 0 说明这条不是成功态，不该有通道待清理。
     *
     * @return 实际更新行数
     */
    int initChannelSyncPending(@Param("requestSignSeq") String requestSignSeq);

    /**
     * 把通道清理的投递结果落库（{@code SUCCESS} / {@code FAILED}）。
     *
     * <p>WHERE 带 {@code NVL(CHANNEL_SYNC_STATUS, 'X') != 'MANUAL'}：{@code MANUAL} 是人工介入态，
     * 自动流程 **NEVER** 覆盖它。返回 0 的正常原因就是这行已被转人工，调用方只记日志、不当失败。
     *
     * @return 实际更新行数
     */
    int updateChannelSyncStatus(@Param("requestSignSeq") String requestSignSeq,
                                @Param("channelSyncStatus") String channelSyncStatus,
                                @Param("channelSyncTime") LocalDateTime channelSyncTime,
                                @Param("channelSyncResult") String channelSyncResult);

    /**
     * 通道清理重试次数 +1 并同时落 {@code FAILED}。
     *
     * <p>补偿重推**前** MUST 先调本方法把记录移出 {@code PENDING}：否则重推后的状态回写若再失败，
     * 这行会被下一轮重复扫到且次数不涨，永远到不了上限、也永远开不出人工介入。
     */
    int increaseChannelSyncRetryCount(@Param("requestSignSeq") String requestSignSeq);

    /**
     * 重试耗尽转人工：置 {@code MANUAL} 并写入原因。
     *
     * <p>WHERE 带 {@code CHANNEL_SYNC_STATUS = 'FAILED'} 是 CAS，只有失败态能转人工。
     * ADR-D8 有意**不建工单表**（pay-sign-server 没有工单表，新建会引入一张只服务单条链路的表；
     * 调 account-server 开单则会新增一条跨域边，正是本次要消除的那类问题）。
     * 因此运维核对 **MUST** 用 {@code CHANNEL_SYNC_STATUS = 'MANUAL'} 过滤本表，
     * 配合 {@code CHANNEL_SYNC_RESULT} 里的原因。
     *
     * @return 实际更新行数，0 表示并发下已被改走
     */
    int markChannelSyncManual(@Param("requestSignSeq") String requestSignSeq,
                              @Param("channelSyncTime") LocalDateTime channelSyncTime,
                              @Param("channelSyncResult") String channelSyncResult);

    /**
     * 取一批需要重推「清理账户域支付通道」的解约申请，按 {@code NVL(CHANNEL_SYNC_TIME, CREATE_TIME)}
     * 升序（最久没成功的先补），并过滤已超重试上限的记录。
     *
     * <p>只捞 {@code TERMINATION_STATUS = 'SUCCESS'}（解约失败的申请没有通道要删）、
     * 且 {@code CHANNEL_SYNC_STATUS} 为 {@code FAILED} 或滞留超 {@code staleMinutes} 的 {@code PENDING}。
     * {@code NULL} 的历史行与 {@code MANUAL} 都**不在**白名单，详见 XML 注释。
     */
    List<AppTerminationRequest> selectCompensableChannelSync(@Param("maxRetryCount") int maxRetryCount,
                                                             @Param("staleMinutes") int staleMinutes,
                                                             @Param("limit") int limit);
}
