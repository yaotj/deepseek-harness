package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 黑名单表数据访问接口。
 */
@Mapper
@Component
public interface BlacklistMapper {
    /**
     * 按卡号列表统计黑名单记录数。
     *
     * @param cardIds 卡号列表
     * @return 命中的黑名单记录数
     */
    int countByCardIds(@Param("cardIds") List<String> cardIds);

    /**
     * 按卡号列表查询黑名单记录。
     *
     * @param cardIds 卡号列表
     * @return 黑名单记录列表
     */
    List<Blacklist> selectByCardIds(@Param("cardIds") List<String> cardIds);

    /**
     * 分页查询黑名单管理记录。
     *
     * @param cardId 卡ID，可选
     * @param thirdUserId 三方用户ID，可选
     * @param createTimeBegin 创建时间起，可选
     * @param createTimeEnd 创建时间止，可选
     * @return 黑名单记录
     */
    List<Blacklist> selectPage(@Param("cardId") String cardId,
                               @Param("thirdUserId") String thirdUserId,
                               @Param("createTimeBegin") String createTimeBegin,
                               @Param("createTimeEnd") String createTimeEnd);

    /**
     * 新增黑名单记录。
     *
     * @param record 黑名单记录
     * @return 影响行数
     */
    int insert(Blacklist record);

    /**
     * 按卡号列表物理删除黑名单记录。
     *
     * @param cardIds 卡号列表
     * @return 影响行数
     */
    int deleteByCardIds(@Param("cardIds") List<String> cardIds);

    /**
     * 分批查询黑名单记录，供「可解除性」只读盘点使用。按 CREATE_TIME 升序取最早的 limit 条。
     *
     * @param limit 单次上限
     * @return 黑名单记录列表
     */
    List<Blacklist> selectForInspect(@Param("limit") int limit);

    /**
     * 扫出待推送渠道的黑名单记录，按最早待推优先。
     *
     * <p>只取 PENDING 与 FAILED 两个状态（白名单），本改造之前拉黑的历史行 CHANNEL_SYNC_STATUS 是
     * NULL、扫不到；REJECTED 是终态也扫不到。NEVER 改成「非 SUCCESS 即扫」。</p>
     *
     * @param limit 单轮最多取多少行
     * @return 待推送的黑名单记录
     */
    List<Blacklist> selectPendingChannelSync(@Param("limit") int limit);

    /**
     * 把渠道同步收口为 SUCCESS，WHERE 带状态白名单做 CAS。
     *
     * @param id 黑名单主键
     * @return 影响行数，0 表示该行已被别处收口
     */
    int markChannelSyncSuccess(@Param("id") Long id);

    /**
     * 把渠道同步收口为失败态并累加重试次数。
     *
     * @param id 黑名单主键
     * @param status FAILED（可重试）或 REJECTED（业务拒绝、终态）
     * @param failReason 失败原因，调用方 MUST 先截断到 500
     * @return 影响行数，0 表示该行已被别处收口
     */
    int markChannelSyncFailed(@Param("id") Long id,
                              @Param("status") String status,
                              @Param("failReason") String failReason);
}
