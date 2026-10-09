package com.chinasofti.huateng.key.mapper;

import com.chinasofti.huateng.key.entity.F2fKeySyncLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AGM 密钥同步日志 Mapper（每个设备一行，UPSERT 模式）。
 */
@Mapper
public interface F2fKeySyncLogMapper {

    /**
     * 插入或更新密钥同步日志（每个设备一行）。
     */
    int upsert(F2fKeySyncLog log);

    /**
     * 按设备查询同步状态。
     */
    F2fKeySyncLog selectByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 查询所有设备同步状态。
     */
    List<F2fKeySyncLog> selectAll();
}
