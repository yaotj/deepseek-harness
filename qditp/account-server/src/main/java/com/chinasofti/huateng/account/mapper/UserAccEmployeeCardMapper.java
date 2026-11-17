package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserAccEmployeeCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface UserAccEmployeeCardMapper {
    UserAccEmployeeCard selectByCardNo(@Param("cardNo") String cardNo);


    List<UserAccEmployeeCard> selectActiveByPhone(@Param("phone") String phone);

    int insert(UserAccEmployeeCard record);

    int update(UserAccEmployeeCard record);

    int updateEmployeeInfo(UserAccEmployeeCard record);


    int updateThirdUserId(@Param("cardNo") String cardNo, @Param("thirdUserId") String thirdUserId);


    int updatePhoneByThirdUserId(@Param("thirdUserId") String thirdUserId, @Param("phone") String phone);
}
