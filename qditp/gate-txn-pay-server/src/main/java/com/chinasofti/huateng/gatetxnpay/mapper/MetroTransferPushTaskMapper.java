package com.chinasofti.huateng.gatetxnpay.mapper;

import com.chinasofti.huateng.gatetxnpay.entity.MetroTransferPushTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MetroTransferPushTaskMapper {
    int insertIgnoreDuplicate(MetroTransferPushTask task);
    List<MetroTransferPushTask> selectReady(@Param("limit") int limit);
    int claim(@Param("id") Long id);
    int markSuccess(@Param("id") Long id);
    int markRetry(@Param("id") Long id, @Param("retryCount") int retryCount,
                  @Param("nextRetryTime") LocalDateTime nextRetryTime, @Param("lastError") String lastError);
    int markFailed(@Param("id") Long id, @Param("retryCount") int retryCount, @Param("lastError") String lastError);
    int recoverStuckProcessing(@Param("leaseSeconds") long leaseSeconds);
}
