package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.BlacklistOperateLog;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;


@Mapper
@Component
public interface BlacklistOperateLogMapper {

    int insert(BlacklistOperateLog record);
}
