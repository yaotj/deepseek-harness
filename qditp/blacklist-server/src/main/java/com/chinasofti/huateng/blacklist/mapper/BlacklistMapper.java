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
     * @param status 行状态 ACTIVE / RELEASING，可选；不传时两种都返回
     * @param channelSyncStatus 渠道同步状态 PENDING / SUCCESS / FAILED / REJECTED，可选
     * @param createTimeBegin 创建时间起，可选
     * @param createTimeEnd 创建时间止，可选
     * @return 黑名单记录
     */
    List<Blacklist> selectPage(@Param("cardId") String cardId,
                               @Param("thirdUserId") String thirdUserId,
                               @Param("status") String status,
                               @Param("channelSyncStatus") String channelSyncStatus,
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
     * 分批查询黑名单记录，供「可解除性」只读盘点使用。按 CREATE_TIME 升序取最早的 limit 条。
     *
     * @param limit 单次上限
     * @return 黑名单记录列表
     */
    List<Blacklist> selectForInspect(@Param("limit") int limit);

    /**
     * 扫出「自动解除」的候选行：生效中、且加黑原因是欠费类（BLACK_CAUSE='01'）。
     *
     * <p>与 {@link #selectForInspect} 的差别只有 BLACK_CAUSE 那一条谓词，但差别是本质的：
     * 盘点是只读的、可以把所有原因都报出来给人看；<b>自动解除会真的改数据，因此 MUST 只覆盖
     * 「加黑原因能被欠费结清证明已失效」的那一类</b>。02 挂失补卡即便欠费清了也 NEVER 自动解除
     * （旧卡会恢复过闸），09 其他是自由文本、无判据。这两类只能人工解除。
     *
     * <p>渠道路由（01 地铁APP 查闸机欠费 / 02 支付宝 查支付宝出行欠费 / 99 未知不处理）<b>刻意不写进
     * 本语句</b>：99 要能被计数并打日志，滤在 SQL 里就看不见「有多少行因为渠道未知而永远解不掉」。
     *
     * @param limit 单次上限
     * @return 候选黑名单记录，按 CREATE_TIME 升序
     */
    List<Blacklist> selectForAutoRelease(@Param("limit") int limit);

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

    /**
     * 扫出待推送渠道的「解除中」记录，按最早待推优先。
     *
     * <p>与 {@link #selectPendingChannelSync} 是对偶：两者共用 CHANNEL_SYNC_* 四列，
     * 靠 STATUS 区分方向（ACTIVE 加黑 / RELEASING 解黑）。改一个 MUST 看另一个。</p>
     *
     * @param limit 单轮最多取多少行
     * @return 待推送解除通知的记录
     */
    List<Blacklist> selectPendingReleaseSync(@Param("limit") int limit);

    /**
     * 解除阶段一：把行从 ACTIVE CAS 成 RELEASING，暂存解除原因与操作者，并把渠道同步重置为 PENDING。
     *
     * <p><b>不搬历史、不删行</b>：解除通知推成功后才进阶段二。RELEASING 的行仍算黑名单，
     * 因此通知没成功前用户不会被提前放行。</p>
     *
     * @param cardId 卡ID
     * @param releaseReason 解除原因
     * @param releaseBy 解除操作者
     * @return 影响行数，0 表示该卡不在黑名单里、或已经在解除中
     */
    int markReleasing(@Param("cardId") String cardId,
                      @Param("releaseReason") String releaseReason,
                      @Param("releaseBy") String releaseBy);

    /**
     * 解除阶段二：删除已确认推达渠道的那一行，WHERE 带 STATUS='RELEASING' 做 CAS。
     *
     * <p><b>NEVER 换成按 CARD_ID 裸删</b> —— 那样通知没成功也会删掉行，等于退回乐观解除。</p>
     *
     * @param id 黑名单主键
     * @return 影响行数，0 表示该行已被别处收口
     */
    int deleteReleasedById(@Param("id") Long id);
}
