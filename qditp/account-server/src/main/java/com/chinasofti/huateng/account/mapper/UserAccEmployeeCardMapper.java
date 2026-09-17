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

    /**
     * 按手机号查该号码下的「活跃」员工码。
     */
    List<UserAccEmployeeCard> selectActiveByPhone(@Param("phone") String phone);

    int insert(UserAccEmployeeCard record);

    int update(UserAccEmployeeCard record);

    int updateEmployeeInfo(UserAccEmployeeCard record);

    /**
     * 把员工码挂到 ITP 用户上。
     */
    int updateThirdUserId(@Param("cardNo") String cardNo, @Param("thirdUserId") String thirdUserId);

    /**
     * 换号时把该 ITP 用户名下所有员工码的 {@code PHONE} 跟着改成新号。
     */
    int updatePhoneByThirdUserId(@Param("thirdUserId") String thirdUserId, @Param("phone") String phone);
}
