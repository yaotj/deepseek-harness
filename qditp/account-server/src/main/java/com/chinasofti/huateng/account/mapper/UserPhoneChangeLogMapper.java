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
     */
    int insert(UserPhoneChangeLog record);

    /**
     * CAS：投递成功。
     *
     * @return 影响行数；0 表示状态已被别人改走，调用方 MUST 检查而非无条件当成功
     */
    int markSignSyncSuccess(@Param("id") Long id,
                            @Param("syncTime") LocalDateTime syncTime,
                            @Param("syncResult") String syncResult);

    /**
     * CAS：投递失败，重试次数 +1。
     *
     * @return 影响行数；0 表示状态已被别人改走
     */
    int markSignSyncFailed(@Param("id") Long id,
                           @Param("syncTime") LocalDateTime syncTime,
                           @Param("syncResult") String syncResult);

    /**
     * CAS：支付域<b>业务拒绝</b>，一次即终态。
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
     * @param maxRetry 重试次数上限（不含），达到即留在 FAILED 等人工介入
     * @param limit    单批条数上限
     */
    List<UserPhoneChangeLog> selectPendingSignSync(@Param("maxRetry") int maxRetry,
                                                  @Param("limit") int limit);
}
