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

    /** 查询用户是否存在待处理解约申请。 */
    AppTerminationRequest selectPendingByUserId(@Param("thirdUserId") String thirdUserId);

    /** 按状态分页取一批解约申请，按 REQUEST_TIME 升序（先申请先处理）。 */
    List<AppTerminationRequest> selectByStatusLimit(@Param("status") String status,
                                                    @Param("limit") int limit);

    /**
     * 按状态 + 申请时间上限取一批解约申请，按 REQUEST_TIME 升序（先申请先处理）。
     *
     * @param cutoff 申请时间截止点，只取该时刻及之前提交的申请
     */
    List<AppTerminationRequest> selectByStatusBefore(@Param("status") String status,
                                                     @Param("cutoff") LocalDateTime cutoff,
                                                     @Param("limit") int limit);

    /** 取一批需要补偿通知的解约申请，按 NVL(NOTIFY_TIME, CREATE_TIME) 升序（最久没成功的先补） */
    List<AppTerminationRequest> selectCompensableNotify(@Param("maxRetryCount") int maxRetryCount,
                                                        @Param("staleMinutes") int staleMinutes,
                                                        @Param("limit") int limit);

    int updateStatus(@Param("requestSignSeq") String requestSignSeq,
                     @Param("status") String status);

    int updateScanTime(@Param("requestSignSeq") String requestSignSeq,
                       @Param("scanTime") LocalDateTime scanTime);

    /**
     * PENDING → SCANNING 抢占：一条语句同时落状态与扫描时间，WHERE 带。
     *
     * @return 实际更新行数，0 表示未命中 PENDING（未抢到执行权）
     */
    int markScanning(@Param("requestSignSeq") String requestSignSeq,
                     @Param("scanTime") LocalDateTime scanTime);

    /**
     * SCANNING → PENDING 回退：支付平台**明确**答复失败时把执行权交还扫表任务重试。
     *
     * @return 实际更新行数，0 表示已被别人改走
     */
    int revertScanningToPending(@Param("requestSignSeq") String requestSignSeq);

    /**
     * PENDING → FAILED 拒绝：置 {@code FAILED} + 失败原因 + 完成时间 + 通知待发，一条语句原子完成。
     *
     * @return 实际更新行数，0 表示未命中 PENDING（已被别人接手）
     */
    int rejectPending(@Param("requestSignSeq") String requestSignSeq,
                      @Param("failReason") String failReason,
                      @Param("completeTime") LocalDateTime completeTime);

    /**
     * SCANNING → SUCCESS 收口：置 {@code SUCCESS} + 完成时间 + 通知待发，一条语句原子完成。
     *
     * @return 实际更新行数，0 表示未命中 SCANNING
     */
    int markSuccess(@Param("requestSignSeq") String requestSignSeq,
                    @Param("completeTime") LocalDateTime completeTime);

    /**
     * SCANNING → FAILED 拒绝：支付平台**明确**答复解约失败时收口，置 {@code FAILED} + 失败原因 +。
     *
     * @return 实际更新行数，0 表示未命中 SCANNING
     */
    int rejectScanning(@Param("requestSignSeq") String requestSignSeq,
                       @Param("failReason") String failReason,
                       @Param("completeTime") LocalDateTime completeTime);

    /**
     * 只取 {@code TERMINATION_STATUS} 一列，供 CAS 返 0 行后回查真实状态。
     *
     * @return 库内当前状态，行不存在时返回 {@code null}
     */
    String selectTerminationStatusBySeq(@Param("requestSignSeq") String requestSignSeq);

    /**
     * {@code markSuccess} 撞上 {@code FAILED} 时，把「需人工核对」落进 {@code FAIL_REASON}。
     *
     * @param manualNote 前置到 {@code FAIL_REASON} 的内部说明，**MUST** 取自
     * @param manualMark 幂等判据，**MUST** 传 {@code TerminationFailReason.MANUAL_REVIEW_MARK}
     * @return 实际更新行数；0 表示未命中 FAILED 或本行已标记过，两种都 **NEVER** 当成失败
     */
    int markConflictForManualReview(@Param("requestSignSeq") String requestSignSeq,
                                    @Param("manualNote") String manualNote,
                                    @Param("manualMark") String manualMark);

    /**
     * {@code rejectScanning} 撞上 {@code SUCCESS} 时留痕：本次回调说解约失败，而库内已收口成成功。
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

    /**
     * 回写本轮通知投递结果。
     *
     * <p>{@code expectedTerminationStatus} 是**轮次闸门**，NEVER 去掉：{@code reactivateFailed} 会把
     * 同一条 {@code REQUEST_SIGN_SEQ} 从 FAILED 复活成 PENDING 并把 {@code NOTIFY_*} 清成新一轮待发，
     * 上一轮迟到的异步回写若无条件落 SUCCESS，新一轮就带着上一轮的成功标记、补偿扫表永远扫不到它。
     *
     * @param expectedTerminationStatus 本次通知所描述的终态，与库内不一致即整条不生效（返 0）
     * @return 实际更新行数，0 表示该轮已被新一轮取代
     */
    int updateNotifyStatus(@Param("requestSignSeq") String requestSignSeq,
                           @Param("expectedTerminationStatus") String expectedTerminationStatus,
                           @Param("notifyStatus") String notifyStatus,
                           @Param("notifyTime") LocalDateTime notifyTime,
                           @Param("notifyResult") String notifyResult);

    /** 同上带轮次闸门：把重试次数记在**本轮**上，NEVER 让上一轮的失败挤掉新一轮的重试预算。 */
    int increaseNotifyRetryCount(@Param("requestSignSeq") String requestSignSeq,
                                 @Param("expectedTerminationStatus") String expectedTerminationStatus);

    /**
     * 把 {@code FAILED} 的解约申请复活成 {@code PENDING}，交给扫表任务重跑。
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
     * @return 实际更新行数，0 表示未命中 SCANNING
     */
    int expireScanning(@Param("requestSignSeq") String requestSignSeq,
                       @Param("failReason") String failReason,
                       @Param("completeTime") LocalDateTime completeTime);

    /**
     * 本地事务收口时把「解约成功后清理账户域支付通道」置为待投递（ADR-D8 第一处）。
     *
     * @return 实际更新行数
     */
    int initChannelSyncPending(@Param("requestSignSeq") String requestSignSeq);

    /**
     * 把通道清理的投递结果落库（{@code SUCCESS} / {@code FAILED}）。
     *
     * @return 实际更新行数
     */
    int updateChannelSyncStatus(@Param("requestSignSeq") String requestSignSeq,
                                @Param("channelSyncStatus") String channelSyncStatus,
                                @Param("channelSyncTime") LocalDateTime channelSyncTime,
                                @Param("channelSyncResult") String channelSyncResult);

    /** 通道清理重试次数 +1 并同时落 {@code FAILED}。 */
    int increaseChannelSyncRetryCount(@Param("requestSignSeq") String requestSignSeq);

    /**
     * 重试耗尽转人工：置 {@code MANUAL} 并写入原因。
     *
     * @return 实际更新行数，0 表示并发下已被改走
     */
    int markChannelSyncManual(@Param("requestSignSeq") String requestSignSeq,
                              @Param("channelSyncTime") LocalDateTime channelSyncTime,
                              @Param("channelSyncResult") String channelSyncResult);

    /** 取一批需要重推「清理账户域支付通道」的解约申请，按 {@code NVL(CHANNEL_SYNC_TIME, CREATE_TIME)}。 */
    List<AppTerminationRequest> selectCompensableChannelSync(@Param("maxRetryCount") int maxRetryCount,
                                                             @Param("staleMinutes") int staleMinutes,
                                                             @Param("limit") int limit);
}
