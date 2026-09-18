package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlipaySignInfoMapper {
    AlipaySignInfo selectByAgreementCode(@Param("agreementCode") String agreementCode);
    AlipaySignInfo selectByChannelAgreementCode(@Param("channelAgreementCode") String channelAgreementCode);
    AlipaySignInfo selectByThirdUserIdAndChannel(@Param("thirdUserId") String thirdUserId, @Param("channel") String channel);

    /**
     * 同键但**不带状态谓词**的查询，只服务 `addContract` 的「表里有没有这一行」判断（ADR-D135）。
     *
     * <p>本表主键是 {@code THIRD_USER_ID} 单列，解约只改状态不删行，因此已解约用户重签时
     * INSERT 必撞主键、只能就地 {@link #reactivateSign} 改那一行。
     * <b>NEVER 拿它替代 {@link #selectByThirdUserIdAndChannel}</b> —— 扣款与对渠道的查询
     * 要的是「生效中的签约」，少了状态谓词就会把 TERMINATED 行当成有效签约用。</p>
     */
    AlipaySignInfo selectAnyByThirdUserIdAndChannel(@Param("thirdUserId") String thirdUserId, @Param("channel") String channel);
    AlipaySignInfo selectByCardIdAndChannel(@Param("cardId") String cardId, @Param("channel") String channel);
    int insert(AlipaySignInfo signInfo);

    /**
     * 签约状态机的 CAS 迁移：{@code SIGNED -> TERMINATED}（ADR-D130）。
     *
     * <p>前置状态写在 SQL 的 WHERE 里，那才是权威白名单。返回 0 行 MUST 交给
     * {@code AlipaySignStatusTransition.classify} 判定，NEVER 当成成功放过去。</p>
     *
     * @return 受影响行数
     */
    int markTerminated(@Param("agreementCode") String agreementCode,
                       @Param("terminationTime") java.time.LocalDateTime terminationTime);

    /** CAS 返 0 行时回查库内真实状态，只用于区分「重复执行」与「状态冲突」。 */
    String selectSignStatusByAgreementCode(@Param("agreementCode") String agreementCode);

    /**
     * 解约后重新签约：状态机的第二条 CAS 迁移 {@code TERMINATED -> SIGNED}（ADR-D135）。
     *
     * <p>之所以是 UPDATE 而不是再 INSERT 一行：主键 {@code THIRD_USER_ID} 单列 + 解约不删行
     * ⇒ 同一用户第二次签约 INSERT 必撞主键。前置状态写在 WHERE 里（与 {@link #markTerminated}
     * 同款白名单），并发下只有一条请求能抢到，其余返 0 行、<b>MUST 回查后按幂等处理，
     * NEVER 把 0 行当成成功</b>。</p>
     *
     * @return 受影响行数
     */
    int reactivateSign(AlipaySignInfo signInfo);

    /**
     * 回写「支付通道同步」outbox 四列（ADR-D129）。
     *
     * <p>{@code CHANNEL_SYNC_RETRY_COUNT} 由 SQL 自增，调用方 NEVER 自己算次数 ——
     * 并发下读出来再加一会丢计数。</p>
     *
     * @param syncStatus PENDING / SUCCESS / FAILED
     * @param syncResult 结果说明，只用于人工排查
     */
    int updateChannelSync(@Param("agreementCode") String agreementCode,
                          @Param("syncStatus") String syncStatus,
                          @Param("syncResult") String syncResult,
                          @Param("syncTime") java.time.LocalDateTime syncTime);

    /**
     * 取一批「支付通道同步」还没收口、且值得重推的签约行（ADR-D132）。
     *
     * <p>三条收窄条件都在 SQL 里，**NEVER 挪到 Java 侧过滤**：挪出去等于每轮把全表拉回来。</p>
     * <ol>
     *   <li>{@code CHANNEL_SYNC_STATUS != 'SUCCESS'} —— 已收口的不再动；</li>
     *   <li>{@code CHANNEL_SYNC_RETRY_COUNT < maxRetryCount} —— 到顶即放弃，等人工；</li>
     *   <li>{@code CHANNEL_SYNC_RESULT NOT LIKE 'BIZ_REJECTED:%'} —— <b>业务拒绝重推一万次也不会成功</b>，
     *       只该开工单。这一条是 {@code BIZ_REJECTED:} / {@code UNREACHABLE:} 那两个前缀存在的唯一理由
     *       （ADR-D131），**改前缀字面量 MUST 同时改这条 SQL**。</li>
     * </ol>
     *
     * <p>排序取 {@code NVL(CHANNEL_SYNC_TIME, CREATE_TIME)}：从没试过的行 {@code CHANNEL_SYNC_TIME}
     * 是 null，不兜底就会被 Oracle 排到最后、永远等不到重推。</p>
     *
     * @param maxRetryCount 重试次数上限（不含）
     * @param batchSize     单轮最多取几行，避免一次拉爆
     */
    java.util.List<AlipaySignInfo> selectCompensableChannelSync(@Param("maxRetryCount") int maxRetryCount,
                                                                @Param("batchSize") int batchSize);
}
