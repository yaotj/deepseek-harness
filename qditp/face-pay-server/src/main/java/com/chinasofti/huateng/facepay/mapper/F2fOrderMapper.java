package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.api.page.FacePayOrderPageVO;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface F2fOrderMapper {

    int insert(F2fOrder order);

    F2fOrder selectByOrderNo(@Param("orderNo") String orderNo);

    String selectOrderStatus(@Param("orderNo") String orderNo);

    F2fOrder selectByQrcode(@Param("deviceId") String deviceId,
                           @Param("qrcodeGenDate") String qrcodeGenDate,
                           @Param("randomFact") String randomFact);

    List<F2fOrder> selectByUserAndActivateFlag(@Param("thirdUserId") String thirdUserId,
                                              @Param("activateFlag") String activateFlag);

    F2fOrder selectLatestByCardId(@Param("cardId") String cardId,
                                  @Param("bizType") String bizType);

    F2fOrder selectByDeviceSeq(@Param("channel") String channel,
                               @Param("deviceId") String deviceId,
                               @Param("deviceSeq") String deviceSeq);

    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("fromStatuses") List<String> fromStatuses,
                     @Param("toStatus") String toStatus,
                     @Param("remark") String remark);

    int markPaid(@Param("orderNo") String orderNo,
                 @Param("paidTms") LocalDateTime paidTms);

    int activateForDevice(@Param("orderNo") String orderNo,
                          @Param("activateDeviceId") String activateDeviceId,
                          @Param("qrcodeGenDate") String qrcodeGenDate,
                          @Param("randomFact") String randomFact,
                          @Param("activateTms") LocalDateTime activateTms);

    List<F2fOrder> selectExpiredCandidates(@Param("earliestExpireTms") LocalDateTime earliestExpireTms,
                                           @Param("now") LocalDateTime now,
                                           @Param("limit") int limit);

    long countStaleExpired(@Param("earliestExpireTms") LocalDateTime earliestExpireTms);

    List<F2fOrder> selectPaidNotFulfilled(@Param("bizTypes") List<String> bizTypes,
                                          @Param("earliest") LocalDateTime earliest,
                                          @Param("deadline") LocalDateTime deadline,
                                          @Param("limit") int limit);

    List<F2fOrder> selectPage(@Param("orderNo") String orderNo,
                              @Param("payCenterOrderNo") String payCenterOrderNo,
                              @Param("payCenterChannelOrderNo") String payCenterChannelOrderNo,
                              @Param("deviceId") String deviceId,
                              @Param("channel") String channel,
                              @Param("bizType") String bizType,
                              @Param("orderStatus") String orderStatus,
                              @Param("beginTime") LocalDateTime beginTime,
                              @Param("endTime") LocalDateTime endTime,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    long countPage(@Param("orderNo") String orderNo,
                   @Param("payCenterOrderNo") String payCenterOrderNo,
                   @Param("payCenterChannelOrderNo") String payCenterChannelOrderNo,
                   @Param("deviceId") String deviceId,
                   @Param("channel") String channel,
                   @Param("bizType") String bizType,
                   @Param("orderStatus") String orderStatus,
                   @Param("beginTime") LocalDateTime beginTime,
                   @Param("endTime") LocalDateTime endTime);

    List<FacePayOrderPageVO> selectPageView(@Param("orderNo") String orderNo,
                                            @Param("payCenterOrderNo") String payCenterOrderNo,
                                            @Param("payCenterChannelOrderNo") String payCenterChannelOrderNo,
                                            @Param("deviceId") String deviceId,
                                            @Param("channel") String channel,
                                            @Param("bizType") String bizType,
                                            @Param("orderStatus") String orderStatus,
                                            @Param("beginTime") LocalDateTime beginTime,
                                            @Param("endTime") LocalDateTime endTime,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    int updateRefundSummary(@Param("orderNo") String orderNo);

    int countUnsettledRefunds(@Param("orderNo") String orderNo);
}
