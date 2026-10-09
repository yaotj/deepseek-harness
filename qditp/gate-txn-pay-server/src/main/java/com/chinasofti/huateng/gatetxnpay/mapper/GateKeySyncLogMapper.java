package com.chinasofti.huateng.gatetxnpay.mapper;

import com.chinasofti.huateng.gatetxnpay.entity.GateKeySyncLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AGM 密钥同步日志 Mapper（每个设备一行，UPSERT 模式）。
 */
@Mapper
public interface GateKeySyncLogMapper {

    /**
     * 插入或更新密钥同步日志（每个设备一行）。
     */
    int upsert(GateKeySyncLog log);

    /**
     * 按设备查询同步状态。
     */
    GateKeySyncLog selectByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 查询所有设备同步状态。
     */
    List<GateKeySyncLog> selectAll();

    /**
     * 统计最近 N 小时的同步情况。
     */
    List<KeySyncStat> countByStatus(@Param("hours") int hours);

    interface KeySyncStat {
        String getStatus();
        Long getCount();
    }
}
