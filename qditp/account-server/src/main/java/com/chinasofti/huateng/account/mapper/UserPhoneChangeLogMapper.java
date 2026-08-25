package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserPhoneChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

/**
 * USER_PHONE_CHANGE_LOG 用户手机号更换历史记录表 Mapper。
 */
@Mapper
@Component
public interface UserPhoneChangeLogMapper {
    /**
     * 插入手机号更换记录。
     */
    int insert(UserPhoneChangeLog record);
}
