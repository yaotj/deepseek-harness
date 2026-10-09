package com.chinasofti.huateng.gatetxnpay.mapper;

import com.chinasofti.huateng.gatetxnpay.entity.GateRetryQueue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户主动重试扣费队列表 Mapper。
 *
 * <p>支持入队、出队、状态更新操作。消费侧通过 CAS 抢占（SELECT FOR UPDATE）保证幂等。
 */
@Mapper
public interface GateRetryQueueMapper {

    /**
     * 入队：幂等插入，同一订单号已存在则跳过。
     *
     * @return 1 插入成功，0 已存在（幂等）
     */
    int insertIgnoreDuplicate(GateRetryQueue queue);

    /**
     * 批量入队：幂等插入，已存在的订单跳过。
     */
    int batchInsertIgnoreDuplicate(@Param("list") List<GateRetryQueue> list);

    /**
     * 扫描待消费队列：按创建时间排序，锁定行防并发消费。
     *
     * @param limit 单次最大扫描笔数
     * @param now   当前时间（用于超时判定）
     * @return 待消费列表
     */
    List<GateRetryQueue> selectPendingForConsume(@Param("limit") int limit,
                                                  @Param("now") LocalDateTime now);

    /**
     * 标记为消费中：CAS 抢占，防止多实例重复消费。
     *
     * @return 1 抢到，0 已被其他实例处理
     */
    int markProcessing(@Param("id") Long id,
                       @Param("now") LocalDateTime now);

    /**
     * 标记完成：扣款成功后更新状态。
     */
    int markDone(@Param("id") Long id,
                 @Param("now") LocalDateTime now);

    /**
     * 标记失败：扣款失败后更新状态，计数 +1。
     */
    int markFailed(@Param("id") Long id,
                   @Param("now") LocalDateTime now,
                   @Param("retryCount") int retryCount,
                   @Param("errorMsg") String errorMsg);

    /**
     * 超时恢复：将超过 N 小时的 PENDING/PROCESSING 状态恢复为 PENDING，允许重新消费。
     */
    int recoverTimeout(@Param("timeoutMinutes") int timeoutMinutes,
                       @Param("now") LocalDateTime now);

    /**
     * 统计队列状态。
     */
    List<QueueStat> countByStatus();

    /** 队列状态统计视图。 */
    interface QueueStat {
        String getStatus();
        Long getCount();
    }
}
