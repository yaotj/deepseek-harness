package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserPayChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@Component
public interface UserPayChannelMapper {
    UserPayChannel selectByThirdUserIdAndCardTypeAndChannel(@Param("thirdUserId") String thirdUserId,
                                                            @Param("cardType") String cardType,
                                                            @Param("channel") String channel);

    List<UserPayChannel> selectByThirdUserIdAndCardTypeAndCardId(@Param("thirdUserId") String thirdUserId,
                                                                 @Param("cardType") String cardType,
                                                                 @Param("cardId") String cardId);


    UserPayChannel selectByReqContractNo(@Param("reqContractNo") String reqContractNo);


    int countByThirdUserId(@Param("thirdUserId") String thirdUserId);

    int insert(UserPayChannel record);


    int updatePayAccountIdByReqContractNo(@Param("reqContractNo") String reqContractNo,
                                         @Param("payAccountId") String payAccountId,
                                         @Param("updateTms") LocalDateTime updateTms);

    int deleteByThirdUserIdAndCardTypeAndChannel(@Param("thirdUserId") String thirdUserId,
                                                 @Param("cardType") String cardType,
                                                 @Param("channel") String channel);
}
