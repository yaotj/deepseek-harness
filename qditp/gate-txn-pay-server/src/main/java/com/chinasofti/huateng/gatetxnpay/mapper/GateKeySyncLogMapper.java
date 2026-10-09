package com.chinasofti.huateng.gatetxnpay.mapper;

import com.chinasofti.huateng.gatetxnpay.entity.GateKeySyncLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AGM 密钥同步日志 Mapper。
 */
@Mapper
public interface GateKeySyncLogMapper {

    /**
     * 插入密钥同步日志。
     */
    int insert(GateKeySyncLog log);

    /**
     * 批量插入密钥同步日志。
     */
    int batchInsert(@Param("list") List<GateKeySyncLog> list);

    /**
     * 按设备查询同步历史。
     */
    List<GateKeySyncLog> selectByDeviceId(@Param("deviceId") String deviceId,
                                           @Param("limit") int limit);

    /**
     * 统计最近 N 小时的同步情况。
     */
    List<KeySyncStat> countByStatus(@Param("hours") int hours);

    interface KeySyncStat {
        String getStatus();
        Long getCount();
    }
}
