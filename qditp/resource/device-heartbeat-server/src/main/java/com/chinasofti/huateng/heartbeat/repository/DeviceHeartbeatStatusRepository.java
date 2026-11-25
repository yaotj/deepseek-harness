package com.chinasofti.huateng.heartbeat.repository;

import com.chinasofti.huateng.heartbeat.model.DeviceHeartbeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 设备心跳状态 Repository（最新状态表，原生 JDBC）
 */
@Repository
public class DeviceHeartbeatStatusRepository {

    private final JdbcTemplate jdbcTemplate;

    public DeviceHeartbeatStatusRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * MERGE INTO：更新或插入设备最新心跳状态
     */
    public int mergeHeartbeat(String channel, String deviceId, String stationCode, LocalDateTime heartbeatTms) {
        String sql = "MERGE INTO DEVICE_HEARTBEAT_STATUS DHS " +
                     "USING (SELECT ? AS CHANNEL, ? AS DEVICE_ID, ? AS STATION_CODE, ? AS LAST_HEARTBEAT_TMS FROM DUAL) SRC " +
                     "ON (DHS.CHANNEL = SRC.CHANNEL AND DHS.DEVICE_ID = SRC.DEVICE_ID) " +
                     "WHEN MATCHED THEN " +
                     "  UPDATE SET STATION_CODE = NVL(SRC.STATION_CODE, DHS.STATION_CODE), " +
                     "             LAST_HEARTBEAT_TMS = SRC.LAST_HEARTBEAT_TMS, " +
                     "             HEARTBEAT_COUNT = DHS.HEARTBEAT_COUNT + 1, " +
                     "             ONLINE_FLAG = '1', " +
                     "             UPDATE_TMS = SYSTIMESTAMP " +
                     "WHEN NOT MATCHED THEN " +
                     "  INSERT (CHANNEL, DEVICE_ID, STATION_CODE, LAST_HEARTBEAT_TMS, " +
                     "          HEARTBEAT_COUNT, ONLINE_FLAG, CREATE_TMS, UPDATE_TMS) " +
                     "  VALUES (SRC.CHANNEL, SRC.DEVICE_ID, SRC.STATION_CODE, SRC.LAST_HEARTBEAT_TMS, " +
                     "          1, '1', SYSTIMESTAMP, SYSTIMESTAMP)";

        return jdbcTemplate.update(sql, channel, deviceId, stationCode, Timestamp.valueOf(heartbeatTms));
    }

    /**
     * 按渠道和设备ID查询
     */
    public DeviceHeartbeatStatus selectByChannelAndDevice(String channel, String deviceId) {
        String sql = "SELECT CHANNEL, DEVICE_ID, STATION_CODE, LAST_HEARTBEAT_TMS, HEARTBEAT_COUNT, " +
                     "ONLINE_FLAG, CREATE_TMS, UPDATE_TMS " +
                     "FROM DEVICE_HEARTBEAT_STATUS " +
                     "WHERE CHANNEL = ? AND DEVICE_ID = ?";

        List<DeviceHeartbeatStatus> list = jdbcTemplate.query(sql,
                (ResultSet rs, int rowNum) -> mapRow(rs),
                channel, deviceId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询超时设备（用于定时任务置离线）
     */
    public List<DeviceHeartbeatStatus> selectTimeoutDevices(LocalDateTime deadline, int limit) {
        String sql = "SELECT CHANNEL, DEVICE_ID, STATION_CODE, LAST_HEARTBEAT_TMS, HEARTBEAT_COUNT, " +
                     "ONLINE_FLAG, CREATE_TMS, UPDATE_TMS " +
                     "FROM DEVICE_HEARTBEAT_STATUS " +
                     "WHERE LAST_HEARTBEAT_TMS < ? AND ONLINE_FLAG = '1' " +
                     "ORDER BY LAST_HEARTBEAT_TMS " +
                     "FETCH FIRST ? ROWS ONLY";

        return jdbcTemplate.query(sql,
                (ResultSet rs, int rowNum) -> mapRow(rs),
                Timestamp.valueOf(deadline), limit);
    }

    /**
     * 批量置离线
     */
    public int markOfflineByDeadline(LocalDateTime deadline) {
        String sql = "UPDATE DEVICE_HEARTBEAT_STATUS " +
                     "SET ONLINE_FLAG = '0', UPDATE_TMS = SYSTIMESTAMP " +
                     "WHERE LAST_HEARTBEAT_TMS < ? AND ONLINE_FLAG = '1'";

        return jdbcTemplate.update(sql, Timestamp.valueOf(deadline));
    }

    /**
     * 按渠道查询设备列表（支持动态过滤）
     */
    public List<DeviceHeartbeatStatus> selectByChannel(String channel, String onlineFlag) {
        String sql = "SELECT CHANNEL, DEVICE_ID, STATION_CODE, LAST_HEARTBEAT_TMS, HEARTBEAT_COUNT, " +
                     "ONLINE_FLAG, CREATE_TMS, UPDATE_TMS " +
                     "FROM DEVICE_HEARTBEAT_STATUS WHERE 1=1";

        List<Object> params = new ArrayList<>();

        if (channel != null && !channel.isBlank()) {
            sql += " AND CHANNEL = ?";
            params.add(channel);
        }
        if (onlineFlag != null && !onlineFlag.isBlank()) {
            sql += " AND ONLINE_FLAG = ?";
            params.add(onlineFlag);
        }

        sql += " ORDER BY LAST_HEARTBEAT_TMS DESC";

        return jdbcTemplate.query(sql,
                (ResultSet rs, int rowNum) -> mapRow(rs),
                params.toArray());
    }

    /**
     * 汇总统计
     */
    public Map<String, Object> selectSummary() {
        String sql = "SELECT COUNT(1) AS total, " +
                     "NVL(SUM(CASE WHEN ONLINE_FLAG = '1' THEN 1 ELSE 0 END), 0) AS onlineCount, " +
                     "NVL(SUM(CASE WHEN ONLINE_FLAG = '0' THEN 1 ELSE 0 END), 0) AS offlineCount, " +
                     "TO_CHAR(MAX(LAST_HEARTBEAT_TMS), 'YYYY-MM-DD HH24:MI:SS') AS lastHeartbeatTms " +
                     "FROM DEVICE_HEARTBEAT_STATUS";

        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
        return list.isEmpty() ? Map.of() : list.get(0);
    }

    @SuppressWarnings("SQLException")
    private DeviceHeartbeatStatus mapRow(ResultSet rs) {
        DeviceHeartbeatStatus status = new DeviceHeartbeatStatus();
        try {
            status.setChannel(rs.getString("CHANNEL"));
            status.setDeviceId(rs.getString("DEVICE_ID"));
            status.setStationCode(rs.getString("STATION_CODE"));
            status.setLastHeartbeatTms(rs.getTimestamp("LAST_HEARTBEAT_TMS").toLocalDateTime());
            status.setHeartbeatCount(rs.getLong("HEARTBEAT_COUNT"));
            status.setOnlineFlag(rs.getString("ONLINE_FLAG"));
            status.setCreateTms(rs.getTimestamp("CREATE_TMS").toLocalDateTime());
            status.setUpdateTms(rs.getTimestamp("UPDATE_TMS").toLocalDateTime());
        } catch (Exception e) {
            throw new RuntimeException("Failed to map heartbeat status row", e);
        }
        return status;
    }
}
