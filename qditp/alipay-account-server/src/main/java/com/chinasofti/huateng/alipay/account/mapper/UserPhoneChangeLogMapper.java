package com.chinasofti.huateng.alipay.account.mapper;

import com.chinasofti.huateng.alipay.account.entity.UserPhoneChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserPhoneChangeLogMapper {
    int insert(UserPhoneChangeLog record);
}
