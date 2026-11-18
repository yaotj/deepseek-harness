package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppTerminationRequestMapper {
    int insert(AppTerminationRequest record);

    AppTerminationRequest selectByRequestSignSeq(@Param("requestSignSeq") String requestSignSeq);

    AppTerminationRequest selectPendingByUserId(@Param("thirdUserId") String thirdUserId);

    List<AppTerminationRequest> selectByStatusLimit(@Param("status") String status,
                                                    @Param("limit") int limit);


    List<AppTerminationRequest> selectByStatusBefore(@Param("status") String status,
                                                     @Param("cutoff") LocalDateTime cutoff,
                                                     @Param("limit") int limit);

    List<AppTerminationRequest> selectCompensableNotify(@Param("maxRetryCount") int maxRetryCount,
                                                        @Param("staleMinutes") int staleMinutes,
                                                        @Param("limit") int limit);

    int updateStatus(@Param("requestSignSeq") String requestSignSeq,
                     @Param("status") String status);

    int updateScanTime(@Param("requestSignSeq") String requestSignSeq,
                       @Param("scanTime") LocalDateTime scanTime);


    int markScanning(@Param("requestSignSeq") String requestSignSeq,
                     @Param("scanTime") LocalDateTime scanTime);


    int revertScanningToPending(@Param("requestSignSeq") String requestSignSeq);


    int rejectPending(@Param("requestSignSeq") String requestSignSeq,
                      @Param("failReason") String failReason,
                      @Param("completeTime") LocalDateTime completeTime);


    int markSuccess(@Param("requestSignSeq") String requestSignSeq,
                    @Param("completeTime") LocalDateTime completeTime);

    int markPendingSuccess(@Param("requestSignSeq") String requestSignSeq,
                           @Param("completeTime") LocalDateTime completeTime);

    int markPendingFailed(@Param("requestSignSeq") String requestSignSeq,
                          @Param("failReason") String failReason,
                          @Param("completeTime") LocalDateTime completeTime);


    int rejectScanning(@Param("requestSignSeq") String requestSignSeq,
                       @Param("failReason") String failReason,
                       @Param("completeTime") LocalDateTime completeTime);


    String selectTerminationStatusBySeq(@Param("requestSignSeq") String requestSignSeq);


    int markConflictForManualReview(@Param("requestSignSeq") String requestSignSeq,
                                    @Param("manualNote") String manualNote,
                                    @Param("manualMark") String manualMark);


    int markFailureConflictForManualReview(@Param("requestSignSeq") String requestSignSeq,
                                           @Param("manualNote") String manualNote,
                                           @Param("manualMark") String manualMark);

    int updateCompleteTime(@Param("requestSignSeq") String requestSignSeq,
                           @Param("completeTime") LocalDateTime completeTime);

    int updateFailReason(@Param("requestSignSeq") String requestSignSeq,
                         @Param("status") String status,
                         @Param("failReason") String failReason);


    int updateNotifyStatus(@Param("requestSignSeq") String requestSignSeq,
                           @Param("expectedTerminationStatus") String expectedTerminationStatus,
                           @Param("notifyStatus") String notifyStatus,
                           @Param("notifyTime") LocalDateTime notifyTime,
                           @Param("notifyResult") String notifyResult);

    int increaseNotifyRetryCount(@Param("requestSignSeq") String requestSignSeq,
                                 @Param("expectedTerminationStatus") String expectedTerminationStatus);


    int reactivateFailed(@Param("requestSignSeq") String requestSignSeq,
                         @Param("cardId") String cardId,
                         @Param("cardType") String cardType,
                         @Param("paymentVendor") String paymentVendor,
                         @Param("requestTime") LocalDateTime requestTime);


    int expireScanning(@Param("requestSignSeq") String requestSignSeq,
                       @Param("failReason") String failReason,
                       @Param("completeTime") LocalDateTime completeTime);


    int initChannelSyncPending(@Param("requestSignSeq") String requestSignSeq);


    int updateChannelSyncStatus(@Param("requestSignSeq") String requestSignSeq,
                                @Param("channelSyncStatus") String channelSyncStatus,
                                @Param("channelSyncTime") LocalDateTime channelSyncTime,
                                @Param("channelSyncResult") String channelSyncResult);

    int increaseChannelSyncRetryCount(@Param("requestSignSeq") String requestSignSeq);


    int markChannelSyncManual(@Param("requestSignSeq") String requestSignSeq,
                              @Param("channelSyncTime") LocalDateTime channelSyncTime,
                              @Param("channelSyncResult") String channelSyncResult);

    List<AppTerminationRequest> selectCompensableChannelSync(@Param("maxRetryCount") int maxRetryCount,
                                                             @Param("staleMinutes") int staleMinutes,
                                                             @Param("limit") int limit);
}
