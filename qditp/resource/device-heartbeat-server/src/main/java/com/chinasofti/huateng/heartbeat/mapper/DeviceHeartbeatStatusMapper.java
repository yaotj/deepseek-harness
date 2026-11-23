package com.chinasofti.huateng.heartbeat.mapper;

import com.chinasofti.huateng.heartbeat.model.DeviceHeartbeatStatus;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备心跳状态 Mapper（最新状态表）
 */
@Mapper
public interface DeviceHeartbeatStatusMapper {

    /**
     * MERGE INTO：更新或插入设备最新心跳状态
     */
    @Insert("""
            MERGE INTO DEVICE_HEARTBEAT_STATUS DHS
            USING (
                SELECT #{channel} AS CHANNEL,
                       #{deviceId} AS DEVICE_ID,
                       #{stationCode} AS STATION_CODE,
                       #{heartbeatTms} AS LAST_HEARTBEAT_TMS
                FROM DUAL
            ) SRC
            ON (DHS.CHANNEL = SRC.CHANNEL AND DHS.DEVICE_ID = SRC.DEVICE_ID)
            WHEN MATCHED THEN
                UPDATE SET STATION_CODE = NVL(SRC.STATION_CODE, DHS.STATION_CODE),
                           LAST_HEARTBEAT_TMS = SRC.LAST_HEARTBEAT_TMS,
                           HEARTBEAT_COUNT = DHS.HEARTBEAT_COUNT + 1,
                           ONLINE_FLAG = '1',
                           UPDATE_TMS = SYSTIMESTAMP
            WHEN NOT MATCHED THEN
                INSERT (CHANNEL, DEVICE_ID, STATION_CODE, LAST_HEARTBEAT_TMS,
                        HEARTBEAT_COUNT, ONLINE_FLAG, CREATE_TMS, UPDATE_TMS)
                VALUES (SRC.CHANNEL, SRC.DEVICE_ID, SRC.STATION_CODE, SRC.LAST_HEARTBEAT_TMS,
                        1, '1', SYSTIMESTAMP, SYSTIMESTAMP)
            """)
    int mergeHeartbeat(@Param("channel") String channel,
                       @Param("deviceId") String deviceId,
                       @Param("stationCode") String stationCode,
                       @Param("heartbeatTms") LocalDateTime heartbeatTms);

    /**
     * 按渠道和设备ID查询
     */
    @Select("SELECT CHANNEL, DEVICE_ID, STATION_CODE, LAST_HEARTBEAT_TMS, HEARTBEAT_COUNT, ONLINE_FLAG, CREATE_TMS, UPDATE_TMS " +
            "FROM DEVICE_HEARTBEAT_STATUS " +
            "WHERE CHANNEL = #{channel} AND DEVICE_ID = #{deviceId}")
    @Results({
            @Result(column = "CHANNEL", property = "channel"),
            @Result(column = "DEVICE_ID", property = "deviceId"),
            @Result(column = "STATION_CODE", property = "stationCode"),
            @Result(column = "LAST_HEARTBEAT_TMS", property = "lastHeartbeatTms"),
            @Result(column = "HEARTBEAT_COUNT", property = "heartbeatCount"),
            @Result(column = "ONLINE_FLAG", property = "onlineFlag"),
            @Result(column = "CREATE_TMS", property = "createTms"),
            @Result(column = "UPDATE_TMS", property = "updateTms")
    })
    DeviceHeartbeatStatus selectByChannelAndDevice(@Param("channel") String channel,
                                                    @Param("deviceId") String deviceId);

    /**
     * 查询超时设备（用于置离线任务）
     */
    @Select("SELECT CHANNEL, DEVICE_ID, STATION_CODE, LAST_HEARTBEAT_TMS, HEARTBEAT_COUNT, ONLINE_FLAG, CREATE_TMS, UPDATE_TMS " +
            "FROM DEVICE_HEARTBEAT_STATUS " +
            "WHERE LAST_HEARTBEAT_TMS < #{deadline} AND ONLINE_FLAG = '1' " +
            "ORDER BY LAST_HEARTBEAT_TMS " +
            "FETCH FIRST #{limit} ROWS ONLY")
    @Results({
            @Result(column = "CHANNEL", property = "channel"),
            @Result(column = "DEVICE_ID", property = "deviceId"),
            @Result(column = "STATION_CODE", property = "stationCode"),
            @Result(column = "LAST_HEARTBEAT_TMS", property = "lastHeartbeatTms"),
            @Result(column = "HEARTBEAT_COUNT", property = "heartbeatCount"),
            @Result(column = "ONLINE_FLAG", property = "onlineFlag"),
            @Result(column = "CREATE_TMS", property = "createTms"),
            @Result(column = "UPDATE_TMS", property = "updateTms")
    })
    List<DeviceHeartbeatStatus> selectTimeoutDevices(@Param("deadline") LocalDateTime deadline,
                                                      @Param("limit") int limit);

    /**
     * 批量置离线
     */
    @Update("""
            UPDATE DEVICE_HEARTBEAT_STATUS
            SET ONLINE_FLAG = '0', UPDATE_TMS = SYSTIMESTAMP
            WHERE LAST_HEARTBEAT_TMS < #{deadline} AND ONLINE_FLAG = '1'
            """)
    int markOfflineByDeadline(@Param("deadline") LocalDateTime deadline);

    /**
     * 按渠道查询设备列表
     */
    @Select("<script>" +
            "SELECT CHANNEL, DEVICE_ID, STATION_CODE, LAST_HEARTBEAT_TMS, HEARTBEAT_COUNT, ONLINE_FLAG, CREATE_TMS, UPDATE_TMS " +
            "FROM DEVICE_HEARTBEAT_STATUS " +
            "<where>" +
            "<if test='channel != null and channel != '''>AND CHANNEL = #{channel}</if>" +
            "<if test='onlineFlag != null and onlineFlag != '''>AND ONLINE_FLAG = #{onlineFlag}</if>" +
            "</where>" +
            "ORDER BY LAST_HEARTBEAT_TMS DESC" +
            "</script>")
    @Results({
            @Result(column = "CHANNEL", property = "channel"),
            @Result(column = "DEVICE_ID", property = "deviceId"),
            @Result(column = "STATION_CODE", property = "stationCode"),
            @Result(column = "LAST_HEARTBEAT_TMS", property = "lastHeartbeatTms"),
            @Result(column = "HEARTBEAT_COUNT", property = "heartbeatCount"),
            @Result(column = "ONLINE_FLAG", property = "onlineFlag"),
            @Result(column = "CREATE_TMS", property = "createTms"),
            @Result(column = "UPDATE_TMS", property = "updateTms")
    })
    List<DeviceHeartbeatStatus> selectByChannel(@Param("channel") String channel,
                                                 @Param("onlineFlag") String onlineFlag);

    /**
     * 汇总统计
     */
    @Select("""
            SELECT COUNT(1) AS total,
                   NVL(SUM(CASE WHEN ONLINE_FLAG = '1' THEN 1 ELSE 0 END), 0) AS onlineCount,
                   NVL(SUM(CASE WHEN ONLINE_FLAG = '0' THEN 1 ELSE 0 END), 0) AS offlineCount,
                   TO_CHAR(MAX(LAST_HEARTBEAT_TMS), 'YYYY-MM-DD HH24:MI:SS') AS lastHeartbeatTms
            FROM DEVICE_HEARTBEAT_STATUS
            """)
    @Results({
            @Result(column = "total", property = "total"),
            @Result(column = "onlineCount", property = "onlineCount"),
            @Result(column = "offlineCount", property = "offlineCount"),
            @Result(column = "lastHeartbeatTms", property = "lastHeartbeatTms")
    })
    java.util.Map<String, Object> selectSummary();
}
