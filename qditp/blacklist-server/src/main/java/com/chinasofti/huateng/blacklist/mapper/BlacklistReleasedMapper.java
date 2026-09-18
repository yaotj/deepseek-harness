package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.BlacklistReleased;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 黑名单解除历史表数据访问接口。
 */
@Mapper
@Component
public interface BlacklistReleasedMapper {
    /**
     * 写入解除快照。与主表 DELETE 必须在同一个本地事务内。
     *
     * @param record 解除快照
     * @return 影响行数
     */
    int insert(BlacklistReleased record);

    /**
     * 按卡号查询解除历史，按解除时间倒序。
     *
     * @param cardId 卡ID
     * @return 解除历史记录
     */
    List<BlacklistReleased> selectByCardId(@Param("cardId") String cardId);

    /**
     * 扫出待推送渠道的解除快照，按最早待推优先。
     *
     * <p>只取 PENDING 与 FAILED 两个状态（白名单），改造前解除的历史行 CHANNEL_SYNC_STATUS 是 NULL、
     * 扫不到；REJECTED 是终态也扫不到。NEVER 改成「非 SUCCESS 即扫」。</p>
     *
     * @param limit 单轮最多取多少行
     * @return 待推送的解除快照
     */
    List<BlacklistReleased> selectPendingChannelSync(@Param("limit") int limit);

    /**
     * 把渠道同步收口为 SUCCESS，WHERE 带状态白名单做 CAS。
     *
     * @param id 解除快照主键
     * @return 影响行数，0 表示该行已被别处收口
     */
    int markChannelSyncSuccess(@Param("id") Long id);

    /**
     * 把渠道同步收口为失败态并累加重试次数。
     *
     * @param id 解除快照主键
     * @param status FAILED（可重试）或 REJECTED（业务拒绝、终态）
     * @param failReason 失败原因，调用方 MUST 先截断到 500
     * @return 影响行数，0 表示该行已被别处收口
     */
    int markChannelSyncFailed(@Param("id") Long id,
                              @Param("status") String status,
                              @Param("failReason") String failReason);
}
