package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserItpRegLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface UserItpRegLogMapper {
    int insert(UserItpRegLog record);


    List<UserItpRegLog> selectCancelLogByFilters(@Param("msisdn") String msisdn,
                                                 @Param("cardId") String cardId,
                                                 @Param("thirdUserId") String thirdUserId);
}
