package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface F2fPaymentMapper {

    int insert(F2fPayment payment);

    F2fPayment selectLastAttempt(@Param("orderNo") String orderNo);

    List<F2fPayment> selectByOrderNo(@Param("orderNo") String orderNo);

    F2fPayment selectByPayCenterOrderNo(@Param("payCenterOrderNo") String payCenterOrderNo);

    int updateCallbackAmounts(@Param("orderNo") String orderNo,
                              @Param("attemptNo") Integer attemptNo,
                              @Param("cashAmount") Integer cashAmount,
                              @Param("couponAmount") Integer couponAmount);

    int markSuccess(@Param("orderNo") String orderNo,
                    @Param("attemptNo") Integer attemptNo,
                    @Param("payCenterOrderNo") String payCenterOrderNo,
                    @Param("channelOrderNo") String channelOrderNo,
                    @Param("payChannelCode") String payChannelCode,
                    @Param("retCode") String retCode,
                    @Param("retMsg") String retMsg,
                    @Param("finishTms") LocalDateTime finishTms);

    int markFinalStatus(@Param("orderNo") String orderNo,
                        @Param("attemptNo") Integer attemptNo,
                        @Param("fromStatuses") List<String> fromStatuses,
                        @Param("toStatus") String toStatus,
                        @Param("retCode") String retCode,
                        @Param("retMsg") String retMsg,
                        @Param("costMs") Integer costMs,
                        @Param("finishTms") LocalDateTime finishTms);

    Integer selectMaxAttemptNo(@Param("orderNo") String orderNo);
}
