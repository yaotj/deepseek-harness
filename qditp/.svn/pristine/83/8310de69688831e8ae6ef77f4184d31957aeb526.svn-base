package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserPayChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

@Mapper
@Component
public interface UserPayChannelMapper {
    UserPayChannel selectByThirdUserIdAndCardTypeAndChannel(@Param("thirdUserId") String thirdUserId,
                                                            @Param("cardType") String cardType,
                                                            @Param("channel") String channel);

    int insert(UserPayChannel record);

    int deleteByThirdUserIdAndCardTypeAndChannel(@Param("thirdUserId") String thirdUserId,
                                                 @Param("cardType") String cardType,
                                                 @Param("channel") String channel);
}
