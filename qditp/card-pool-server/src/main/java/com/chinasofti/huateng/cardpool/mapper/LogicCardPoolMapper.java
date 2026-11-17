package com.chinasofti.huateng.cardpool.mapper;

import com.chinasofti.huateng.cardpool.entity.LogicCardPoolBatch;
import com.chinasofti.huateng.cardpool.entity.LogicCardPoolCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LogicCardPoolMapper {


    Long nextBatchNo();


    int insertBatch(LogicCardPoolBatch batch);


    LogicCardPoolBatch selectBatch(@Param("batchNo") Long batchNo);


    List<LogicCardPoolBatch> selectBatches(@Param("batchNo") Long batchNo,
                                           @Param("cardType") String cardType,
                                           @Param("accTicketType") String accTicketType,
                                           @Param("source") String source,
                                           @Param("status") String status,
                                           @Param("createTimeBegin") LocalDateTime createTimeBegin,
                                           @Param("createTimeEnd") LocalDateTime createTimeEnd);


    List<LogicCardPoolBatch> selectPendingBatches(@Param("cardType") String cardType);


    int countInProgress(@Param("cardType") String cardType);


    int acquireBatchLock(@Param("cardType") String cardType,
                         @Param("lockOwner") String lockOwner,
                         @Param("staleSeconds") long staleSeconds);


    int renewBatchLock(@Param("cardType") String cardType, @Param("lockOwner") String lockOwner);


    int releaseBatchLock(@Param("cardType") String cardType, @Param("lockOwner") String lockOwner);


    int updateBatch(LogicCardPoolBatch batch);


    int markBatchFailed(@Param("batchNo") Long batchNo, @Param("errorMsg") String errorMsg);


    int markBatchRetrying(@Param("batchNo") Long batchNo);


    int insertCards(@Param("cards") List<LogicCardPoolCard> cards);


    long countByTypeAndStatus(@Param("cardType") String cardType, @Param("status") String status);


    LogicCardPoolCard selectByBusiness(@Param("businessType") String businessType,
                                       @Param("businessId") String businessId);


    List<LogicCardPoolCard> selectAvailableCandidates(@Param("cardType") String cardType,
                                                      @Param("limit") int limit);


    int reserve(@Param("id") Long id,
                @Param("reservationId") String reservationId,
                @Param("businessType") String businessType,
                @Param("businessId") String businessId,
                @Param("ownerId") String ownerId,
                @Param("expireTime") LocalDateTime expireTime);


    int confirm(@Param("reservationId") String reservationId, @Param("businessId") String businessId);


    int release(@Param("reservationId") String reservationId, @Param("businessId") String businessId);


    int releaseExpired();


    LogicCardPoolCard selectByCardNo(@Param("cardNo") String cardNo);


    LogicCardPoolCard selectByReservation(@Param("reservationId") String reservationId);
}
