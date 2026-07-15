package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.BlacklistOperateLog;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

/**
 * 黑名单操作记录表数据访问接口。
 */
@Mapper
@Component
public interface BlacklistOperateLogMapper {
    /**
     * 新增黑名单操作记录。
     *
     * @param record 黑名单操作记录
     * @return 影响行数
     */
    int insert(BlacklistOperateLog record);
}
