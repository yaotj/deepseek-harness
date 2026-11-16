package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.HceBlacklistFileState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

/**
 * HCE 黑名单文件版本号状态表数据访问接口。
 */
@Mapper
@Component
public interface HceBlacklistFileStateMapper {

    /**
     * 按参数类型锁定读取状态行（SELECT ... FOR UPDATE），供版本号分配使用。
     *
     * @param paramType 参数类型
     * @return 状态行；从未分配过时为 null
     */
    HceBlacklistFileState selectForUpdate(@Param("paramType") String paramType);

    /**
     * 初始化状态行。
     *
     * @param paramType 参数类型
     * @param currentVersion 初始版本号
     * @return 影响行数
     */
    int insert(@Param("paramType") String paramType, @Param("currentVersion") Long currentVersion);

    /**
     * 回写已分配的最新版本号。
     *
     * @param paramType 参数类型
     * @param currentVersion 最新版本号
     * @return 影响行数
     */
    int updateVersion(@Param("paramType") String paramType, @Param("currentVersion") Long currentVersion);
}
