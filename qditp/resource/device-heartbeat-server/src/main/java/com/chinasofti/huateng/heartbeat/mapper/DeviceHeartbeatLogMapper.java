package com.chinasofti.huateng.heartbeat.mapper;

import com.chinasofti.huateng.heartbeat.model.DeviceHeartbeatLog;
import org.apache.ibatis.annotations.*;

/**
 * 设备心跳日志 Mapper
 */
@Mapper
public interface DeviceHeartbeatLogMapper {

    /**
     * 插入心跳历史日志
     */
    @Insert("INSERT INTO DEVICE_HEARTBEAT_LOG (ID, CHANNEL, DEVICE_ID, STATION_CODE, HEARTBEAT_TMS, REQ_BIZ_DATA, CREATE_TMS) " +
            "VALUES (DEVICE_HEARTBEAT_LOG_SEQ.NEXTVAL, #{channel}, #{deviceId}, #{stationCode}, #{heartbeatTms}, #{reqBizData}, SYSTIMESTAMP)")
    @Options(useGeneratedKeys = false)
    int insertLog(DeviceHeartbeatLog log);

    /**
     * 查询最新ID（用于验证）
     */
    @Select("SELECT MAX(ID) FROM DEVICE_HEARTBEAT_LOG")
    Long selectMaxId();

    /**
     * 按时间范围查询心跳记录
     */
    @Select("SELECT ID, CHANNEL, DEVICE_ID, STATION_CODE, HEARTBEAT_TMS, REQ_BIZ_DATA, CREATE_TMS " +
            "FROM DEVICE_HEARTBEAT_LOG " +
            "WHERE HEARTBEAT_TMS BETWEEN #{startTime} AND #{endTime} " +
            "ORDER BY HEARTBEAT_TMS DESC")
    @Results({
            @Result(column = "ID", property = "id"),
            @Result(column = "CHANNEL", property = "channel"),
            @Result(column = "DEVICE_ID", property = "deviceId"),
            @Result(column = "STATION_CODE", property = "stationCode"),
            @Result(column = "HEARTBEAT_TMS", property = "heartbeatTms"),
            @Result(column = "REQ_BIZ_DATA", property = "reqBizData"),
            @Result(column = "CREATE_TMS", property = "createTms")
    })
    java.util.List<DeviceHeartbeatLog> selectByTimeRange(@Param("startTime") java.time.LocalDateTime startTime,
                                                         @Param("endTime") java.time.LocalDateTime endTime);
}
