package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fRefund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface F2fRefundMapper {

    int insert(F2fRefund refund);

    F2fRefund selectByRefundNo(@Param("refundNo") String refundNo);

    List<F2fRefund> selectByOrigOrderNo(@Param("origOrderNo") String origOrderNo);

    F2fRefund selectByPayCenterRefundNo(@Param("payCenterRefundNo") String payCenterRefundNo);

    int updateStatus(@Param("refundNo") String refundNo,
                     @Param("fromStatuses") List<String> fromStatuses,
                     @Param("toStatus") String toStatus,
                     @Param("payCenterRefundNo") String payCenterRefundNo,
                     @Param("finishTms") LocalDateTime finishTms);

    List<F2fRefund> selectRetryCandidates(@Param("statuses") List<String> statuses,
                                          @Param("now") LocalDateTime now,
                                          @Param("limit") int limit);

    int increaseRetryTimes(@Param("refundNo") String refundNo,
                           @Param("failReason") String failReason,
                           @Param("nextQueryTms") LocalDateTime nextQueryTms);
}
