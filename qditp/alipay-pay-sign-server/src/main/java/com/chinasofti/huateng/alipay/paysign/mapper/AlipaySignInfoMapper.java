package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlipaySignInfoMapper {
    AlipaySignInfo selectByAgreementCode(@Param("agreementCode") String agreementCode);
    AlipaySignInfo selectByChannelAgreementCode(@Param("channelAgreementCode") String channelAgreementCode);
    AlipaySignInfo selectByThirdUserIdAndChannel(@Param("thirdUserId") String thirdUserId, @Param("channel") String channel);


    AlipaySignInfo selectAnyByThirdUserIdAndChannel(@Param("thirdUserId") String thirdUserId, @Param("channel") String channel);
    AlipaySignInfo selectByCardIdAndChannel(@Param("cardId") String cardId, @Param("channel") String channel);
    int insert(AlipaySignInfo signInfo);


    int markTerminated(@Param("agreementCode") String agreementCode,
                       @Param("terminationTime") java.time.LocalDateTime terminationTime);

    String selectSignStatusByAgreementCode(@Param("agreementCode") String agreementCode);


    int reactivateSign(AlipaySignInfo signInfo);


    int updateChannelSync(@Param("agreementCode") String agreementCode,
                          @Param("syncStatus") String syncStatus,
                          @Param("syncResult") String syncResult,
                          @Param("syncTime") java.time.LocalDateTime syncTime);


    java.util.List<AlipaySignInfo> selectCompensableChannelSync(@Param("maxRetryCount") int maxRetryCount,
                                                                @Param("batchSize") int batchSize);


    java.util.List<AlipaySignInfo> selectPageForOps(@Param("thirdUserId") String thirdUserId,
                                                    @Param("cardId") String cardId,
                                                    @Param("signStatus") String signStatus,
                                                    @Param("signTimeBegin") String signTimeBegin,
                                                    @Param("signTimeEnd") String signTimeEnd,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    int countPageForOps(@Param("thirdUserId") String thirdUserId,
                        @Param("cardId") String cardId,
                        @Param("signStatus") String signStatus,
                        @Param("signTimeBegin") String signTimeBegin,
                        @Param("signTimeEnd") String signTimeEnd);
}
