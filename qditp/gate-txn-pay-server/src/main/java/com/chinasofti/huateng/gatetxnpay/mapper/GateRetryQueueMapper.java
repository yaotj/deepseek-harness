package com.chinasofti.huateng.gatetxnpay.mapper;

import com.chinasofti.huateng.gatetxnpay.entity.GateRetryQueue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;


@Mapper
public interface GateRetryQueueMapper {


    int insertIgnoreDuplicate(GateRetryQueue queue);


    int batchInsertIgnoreDuplicate(@Param("list") List<GateRetryQueue> list);


    List<GateRetryQueue> selectPendingForConsume(@Param("limit") int limit,
                                                  @Param("now") LocalDateTime now);


    int markProcessing(@Param("id") Long id,
                       @Param("now") LocalDateTime now);


    int markDone(@Param("id") Long id,
                 @Param("now") LocalDateTime now);


    int markFailed(@Param("id") Long id,
                   @Param("now") LocalDateTime now,
                   @Param("retryCount") int retryCount,
                   @Param("errorMsg") String errorMsg);


    int recoverTimeout(@Param("timeoutMinutes") int timeoutMinutes,
                       @Param("now") LocalDateTime now);


    List<QueueStat> countByStatus();

    interface QueueStat {
        String getStatus();
        Long getCount();
    }
}
