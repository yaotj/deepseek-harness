package com.chinasofti.huateng.cardpool.mapper;

import com.chinasofti.huateng.cardpool.entity.LogicCardPoolBatch;
import com.chinasofti.huateng.cardpool.entity.LogicCardPoolCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 逻辑卡号池批次与明细数据访问接口。
 */
@Mapper
public interface LogicCardPoolMapper {

    /**
     * 取下一个批次号。
     *
     * @return 批次号
     */
    Long nextBatchNo();

    /**
     * 新增批次记录，五个计数列与重试次数由 SQL 显式写 0，避免后续全字段更新写入 NULL。
     *
     * @param batch 批次
     * @return 影响行数
     */
    int insertBatch(LogicCardPoolBatch batch);

    /**
     * 按批次号查询批次。
     *
     * @param batchNo 批次号
     * @return 批次，不存在返回 null
     */
    LogicCardPoolBatch selectBatch(@Param("batchNo") Long batchNo);

    /**
     * 按条件分页查询批次，按创建时间倒序。
     *
     * @param batchNo         批次号
     * @param cardType        票种
     * @param accTicketType   ACC 票种
     * @param source          来源 AUTO / MANUAL
     * @param status          批次状态
     * @param createTimeBegin 创建时间起
     * @param createTimeEnd   创建时间止
     * @return 批次列表
     */
    List<LogicCardPoolBatch> selectBatches(@Param("batchNo") Long batchNo,
                                           @Param("cardType") String cardType,
                                           @Param("accTicketType") String accTicketType,
                                           @Param("source") String source,
                                           @Param("status") String status,
                                           @Param("createTimeBegin") LocalDateTime createTimeBegin,
                                           @Param("createTimeEnd") LocalDateTime createTimeEnd);

    /**
     * 查询待推进的批次（CREATED / REQUESTING / DOWNLOADING / IMPORTING），按批次号升序。
     *
     * @param cardType 票种，为 null 时查全部票种
     * @return 待推进批次列表
     */
    List<LogicCardPoolBatch> selectPendingBatches(@Param("cardType") String cardType);

    /**
     * 统计某票种进行中的批次数量，用于避免重复申请。
     *
     * @param cardType 票种
     * @return 进行中批次数量
     */
    int countInProgress(@Param("cardType") String cardType);

    /**
     * 抢占票种级批次锁；持有超过 staleSeconds 未续期的锁可被抢占。
     *
     * @param cardType     票种
     * @param lockOwner    锁持有者标识
     * @param staleSeconds 判定锁失效的秒数
     * @return 抢到锁返回 1，否则返回 0
     */
    int acquireBatchLock(@Param("cardType") String cardType,
                         @Param("lockOwner") String lockOwner,
                         @Param("staleSeconds") long staleSeconds);

    /**
     * 续期票种级批次锁，长耗时导入期间定期调用以避免被其它副本抢占。
     *
     * @param cardType  票种
     * @param lockOwner 锁持有者标识
     * @return 影响行数
     */
    int renewBatchLock(@Param("cardType") String cardType, @Param("lockOwner") String lockOwner);

    /**
     * 释放票种级批次锁，仅当持有者匹配时生效。
     *
     * @param cardType  票种
     * @param lockOwner 锁持有者标识
     * @return 影响行数
     */
    int releaseBatchLock(@Param("cardType") String cardType, @Param("lockOwner") String lockOwner);

    /**
     * 更新批次；五个计数列与重试次数为 null 时不参与更新，保留库中原值。
     *
     * @param batch 批次
     * @return 影响行数
     */
    int updateBatch(LogicCardPoolBatch batch);

    /**
     * 只写终态三列的最小化失败落库，供 {@link #updateBatch} 失败后降级调用。
     *
     * <p>存在意义：`updateBatch` 会写 10 余列，任一列越长或类型不匹配都会整条失败，
     * 批次就会卡在 REQUESTING/IMPORTING 中间态并把该票种的补货永久挡死。
     * 本方法只碰 STATUS / ERROR_MSG / FINISH_TIME，把「一定要离开中间态」这件事的失败面降到最小。</p>
     *
     * @param batchNo  批次号
     * @param errorMsg 失败原因，调用方需自行按列长截断
     * @return 影响行数
     */
    int markBatchFailed(@Param("batchNo") Long batchNo, @Param("errorMsg") String errorMsg);

    /**
     * 以状态条件推进失败批次进入重试，重试次数在 SQL 内自增，保证并发下不丢次数。
     *
     * @param batchNo 批次号
     * @return 命中并推进返回 1，状态不满足返回 0
     */
    int markBatchRetrying(@Param("batchNo") Long batchNo);

    /**
     * 批量插入逻辑卡号明细。
     *
     * @param cards 卡号列表
     * @return 影响行数
     */
    int insertCards(@Param("cards") List<LogicCardPoolCard> cards);

    /**
     * 统计某票种某状态的卡号数量。
     *
     * @param cardType 票种
     * @param status   状态
     * @return 数量
     */
    long countByTypeAndStatus(@Param("cardType") String cardType, @Param("status") String status);

    /**
     * 按业务归属查询已占用的卡号，用于预占幂等。
     *
     * @param businessType 业务类型
     * @param businessId   业务流水号
     * @return 卡号记录，不存在返回 null
     */
    LogicCardPoolCard selectByBusiness(@Param("businessType") String businessType,
                                       @Param("businessId") String businessId);

    /**
     * 取某票种按 ID 升序的前若干条可用卡号作为预占候选。
     *
     * <p>刻意返回多行：若固定只取最小 ID，并发预占会全部命中同一行并阻塞在行锁上，
     * 同票种预占退化为串行。调用方应从候选中随机挑选后走条件 UPDATE 做 CAS。</p>
     *
     * @param cardType 票种
     * @param limit    候选条数上限
     * @return 可用卡号候选，无可用返回空列表
     */
    List<LogicCardPoolCard> selectAvailableCandidates(@Param("cardType") String cardType,
                                                      @Param("limit") int limit);

    /**
     * 预占卡号，仅当状态仍为 AVAILABLE 时生效。
     *
     * @param id            卡号主键
     * @param reservationId 预占标识
     * @param businessType  业务类型
     * @param businessId    业务流水号
     * @param ownerId       归属方标识
     * @param expireTime    预占过期时间
     * @return 预占成功返回 1，已被他人抢占返回 0
     */
    int reserve(@Param("id") Long id,
                @Param("reservationId") String reservationId,
                @Param("businessType") String businessType,
                @Param("businessId") String businessId,
                @Param("ownerId") String ownerId,
                @Param("expireTime") LocalDateTime expireTime);

    /**
     * 确认预占，仅当状态为 RESERVED 时生效。
     *
     * @param reservationId 预占标识
     * @param businessId    业务流水号
     * @return 影响行数
     */
    int confirm(@Param("reservationId") String reservationId, @Param("businessId") String businessId);

    /**
     * 释放预占，仅当状态为 RESERVED 时生效。
     *
     * @param reservationId 预占标识
     * @param businessId    业务流水号
     * @return 影响行数
     */
    int release(@Param("reservationId") String reservationId, @Param("businessId") String businessId);

    /**
     * 回收所有已过期的预占。
     *
     * @return 回收数量
     */
    int releaseExpired();

    /**
     * 按卡号查询明细，用于导入时区分重复与其它约束冲突。
     *
     * @param cardNo 逻辑卡号
     * @return 卡号记录，不存在返回 null
     */
    LogicCardPoolCard selectByCardNo(@Param("cardNo") String cardNo);

    /**
     * 按预占标识查询明细。
     *
     * @param reservationId 预占标识
     * @return 卡号记录，不存在返回 null
     */
    LogicCardPoolCard selectByReservation(@Param("reservationId") String reservationId);
}
