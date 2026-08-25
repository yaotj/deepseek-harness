package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AppTerminationRequestMapper {
    int insert(AppTerminationRequest record);

    AppTerminationRequest selectByRequestSignSeq(@Param("requestSignSeq") String requestSignSeq);

    /**
     * 查询用户是否存在待处理解约申请
     * 查询条件：TERMINATION_STATUS IN ('PENDING', 'SCANNING')
     */
    AppTerminationRequest selectPendingByUserId(@Param("thirdUserId") String thirdUserId);

    int updateStatus(@Param("requestSignSeq") String requestSignSeq,
                     @Param("status") String status);

    int updateScanTime(@Param("requestSignSeq") String requestSignSeq,
                       @Param("scanTime") LocalDateTime scanTime);

    int updateCompleteTime(@Param("requestSignSeq") String requestSignSeq,
                           @Param("completeTime") LocalDateTime completeTime);

    int updateFailReason(@Param("requestSignSeq") String requestSignSeq,
                         @Param("status") String status,
                         @Param("failReason") String failReason);

    int updateNotifyStatus(@Param("requestSignSeq") String requestSignSeq,
                           @Param("notifyStatus") String notifyStatus,
                           @Param("notifyTime") LocalDateTime notifyTime,
                           @Param("notifyResult") String notifyResult);

    int increaseNotifyRetryCount(@Param("requestSignSeq") String requestSignSeq);
}
