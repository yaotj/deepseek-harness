package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;


@Mapper
@Component
public interface BlacklistMapper {

    int countByCardIds(@Param("cardIds") List<String> cardIds);


    List<Blacklist> selectByCardIds(@Param("cardIds") List<String> cardIds);


    List<Blacklist> selectPage(@Param("cardId") String cardId,
                               @Param("thirdUserId") String thirdUserId,
                               @Param("status") String status,
                               @Param("channelSyncStatus") String channelSyncStatus,
                               @Param("createTimeBegin") String createTimeBegin,
                               @Param("createTimeEnd") String createTimeEnd);


    int insert(Blacklist record);


    List<Blacklist> selectForInspect(@Param("limit") int limit);


    List<Blacklist> selectForAutoRelease(@Param("limit") int limit);


    List<Blacklist> selectPendingChannelSync(@Param("limit") int limit);


    int markChannelSyncSuccess(@Param("id") Long id);


    int markChannelSyncFailed(@Param("id") Long id,
                              @Param("status") String status,
                              @Param("failReason") String failReason);


    List<Blacklist> selectPendingReleaseSync(@Param("limit") int limit);


    int markReleasing(@Param("cardId") String cardId,
                      @Param("releaseReason") String releaseReason,
                      @Param("releaseBy") String releaseBy);


    List<Blacklist> selectHceBlacklist();


    int deleteReleasedById(@Param("id") Long id);
}
