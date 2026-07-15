package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

@Mapper
@Component
public interface UserItpRegInfoMapper {
    UserItpRegInfo selectActiveByThirdUserId(@Param("thirdUserId") String thirdUserId);

    int updateDefaultPayChannelById(UserItpRegInfo record);

    int clearDefaultPayChannelById(@Param("id") Integer id);

    int insert(UserItpRegInfo record);
}
