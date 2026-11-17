package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserPhoneChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;


@Mapper
@Component
public interface UserPhoneChangeLogMapper {

    int insert(UserPhoneChangeLog record);


    int markSignSyncSuccess(@Param("id") Long id,
                            @Param("syncTime") LocalDateTime syncTime,
                            @Param("syncResult") String syncResult);


    int markSignSyncFailed(@Param("id") Long id,
                           @Param("syncTime") LocalDateTime syncTime,
                           @Param("syncResult") String syncResult);


    int markSignSyncRejected(@Param("id") Long id,
                             @Param("syncTime") LocalDateTime syncTime,
                             @Param("syncResult") String syncResult,
                             @Param("terminalRetryCount") int terminalRetryCount);


    List<UserPhoneChangeLog> selectPendingSignSync(@Param("maxRetry") int maxRetry,
                                                  @Param("limit") int limit);
}
