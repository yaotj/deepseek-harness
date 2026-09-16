package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserPhoneChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * USER_PHONE_CHANGE_LOG 用户手机号更换历史记录表 Mapper。
 */
@Mapper
@Component
public interface UserPhoneChangeLogMapper {
    /**
     * 插入手机号更换记录。
     *
     * <p>主键由 {@code <selectKey order="BEFORE">} 先取 {@code SEQ_USER_PHONE_CHANGE_LOG.NEXTVAL}
     * 再回填到 {@code record.id}，调用方 insert 后可直接用 {@link UserPhoneChangeLog#getId()}
     * 定位本行去落投递状态。**NEVER 改回 useGeneratedKeys**，见 mapper XML 注释。</p>
     */
    int insert(UserPhoneChangeLog record);

    /**
     * CAS：投递成功。仅 {@code SIGN_SYNC_STATUS = 'PENDING'} 或 {@code 'FAILED'} 时生效。
     *
     * @return 影响行数；0 表示状态已被别人改走，调用方 MUST 检查而非无条件当成功
     */
    int markSignSyncSuccess(@Param("id") Long id,
                            @Param("syncTime") LocalDateTime syncTime,
                            @Param("syncResult") String syncResult);

    /**
     * CAS：投递失败，重试次数 +1。仅 {@code SIGN_SYNC_STATUS = 'PENDING'} 或 {@code 'FAILED'} 时生效。
     *
     * <p>NEVER 允许从 {@code SUCCESS} 改成 {@code FAILED} —— 已送达的事实不能被迟到的失败覆盖。</p>
     *
     * @return 影响行数；0 表示状态已被别人改走
     */
    int markSignSyncFailed(@Param("id") Long id,
                           @Param("syncTime") LocalDateTime syncTime,
                           @Param("syncResult") String syncResult);

    /**
     * CAS：支付域<b>业务拒绝</b>，一次即终态。
     *
     * <p>与 {@link #markSignSyncFailed} 的唯一区别是重试次数直接置成上限而非 +1 —— 终态靠
     * {@link #selectPendingSignSync} 的「重试次数未达上限」过滤实现，置到上限即不再被扫到，
     * 与自然重试耗尽走同一条机制。<b>NEVER 为「拒绝」新增状态值</b>，理由见 mapper XML 注释。</p>
     *
     * <p>「业务拒绝」指对端答复了但拒绝处理（如该用户在支付域没有签约记录），重推永远不会成功；
     * 与「未获业务答复」（连不上 / 超时 / HTTP 错误）是两类，后者 MUST 仍走
     * {@link #markSignSyncFailed} 进补偿队列。判定由 {@code RpcOutcome} 的模式匹配保证。</p>
     *
     * <p>调用方 <b>MUST 在本方法之后立即开工单</b>：该行此后不再被扫表捞到。</p>
     *
     * @param terminalRetryCount 直接写入的重试次数，取补偿扫表的上限值
     * @return 影响行数；0 表示状态已被别人改走
     */
    int markSignSyncRejected(@Param("id") Long id,
                             @Param("syncTime") LocalDateTime syncTime,
                             @Param("syncResult") String syncResult,
                             @Param("terminalRetryCount") int terminalRetryCount);

    /**
     * 补偿扫表：捞出待重推的行（{@code SIGN_SYNC_STATUS IN ('PENDING','FAILED')}
     * 且重试次数未达上限），按 {@code ID} 升序、最多 {@code limit} 条。
     *
     * <p>只回填 {@code id} / {@code thirdUserId} / {@code newMsisdn} / {@code signSyncStatus}
     * / {@code signSyncRetryCount} 五个字段 —— 重推只需要这些，NEVER 为此把整行捞出来。</p>
     *
     * <p>{@code SIGN_SYNC_STATUS} 为 NULL 的历史行永不被捞取，见 mapper XML 注释。</p>
     *
     * @param maxRetry 重试次数上限（不含），达到即留在 FAILED 等人工介入
     * @param limit    单批条数上限
     */
    List<UserPhoneChangeLog> selectPendingSignSync(@Param("maxRetry") int maxRetry,
                                                  @Param("limit") int limit);
}
