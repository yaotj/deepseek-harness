package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fDeviceStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备心跳状态 Mapper（表 F2F_DEVICE_STATUS）。
 *
 * <p>本表特有的约束，改这个接口前 MUST 先读：
 * <ul>
 *   <li><b>心跳更新只能用 MERGE INTO，不是 insert / update 两步。</b>
 *       主键是复合主键 {@code (CHANNEL, DEVICE_ID)}，一台设备只有一行。
 *       先 SELECT 判断存在再决定 insert 还是 update，在每 1 分钟并发心跳下会撞主键
 *       （同一设备重连、多线程重投都会命中同一行）；本项目的约定是
 *       「唯一索引 / 主键 + MERGE INTO」而不是应用层判断，见 AGENTS.md §2.2.1。</li>
 *   <li><b>只存最新状态不存历史。</b>MERGE 命中已有行时覆盖车站与心跳时间、
 *       {@code HEARTBEAT_COUNT} 自增，NEVER 追加新行。</li>
 *   <li><b>离线判定不在心跳链路里做</b>，由 {@link #selectHeartbeatTimeout} +
 *       {@link #markOfflineByDeadline} 的扫表任务完成，命中 IDX_F2F_DEVICE_HB。</li>
 * </ul>
 */
@Mapper
public interface F2fDeviceStatusMapper {

    /**
     * 心跳上报 upsert：单条 MERGE INTO 完成「有则更新、无则插入」。
     *
     * <p>命中已有行时：{@code LAST_HEARTBEAT_TMS} 更新为本次心跳时间、
     * {@code HEARTBEAT_COUNT} 自增 1、{@code ONLINE_FLAG} 置 '1'（超时置离线后重新上报即自动恢复在线）。
     * 未命中时按 {@code HEARTBEAT_COUNT = 1}、{@code ONLINE_FLAG = '1'} 插入首行。
     *
     * @param channel          受理渠道，复合主键第一列
     * @param deviceId         设备号，复合主键第二列
     * @param stationCode      车站编码，可为空；非空时覆盖已有值
     * @param heartbeatTms     本次心跳时间
     * @return 影响行数，正常为 1（MERGE 走 UPDATE 分支或 INSERT 分支都是 1）
     */
    int mergeHeartbeat(@Param("channel") String channel,
                       @Param("deviceId") String deviceId,
                       @Param("stationCode") String stationCode,
                       @Param("heartbeatTms") LocalDateTime heartbeatTms);

    /**
     * 按复合主键查单台设备的最新状态，供设备状态查询与离线判定复核使用。
     *
     * @return 该设备唯一一行；从未上报过心跳返回 null
     */
    F2fDeviceStatus selectByChannelAndDevice(@Param("channel") String channel,
                                            @Param("deviceId") String deviceId);

    /**
     * 扫出心跳超时但仍标记在线的设备，供置离线任务使用。命中 IDX_F2F_DEVICE_HB。
     *
     * <p>条件为 {@code LAST_HEARTBEAT_TMS} 早于 deadline 且 {@code ONLINE_FLAG = '1'}；
     * 已置离线的行不会重复返回。deadline 由调用方按「心跳周期 × 容忍倍数」算出，本语句不内置时间窗。
     *
     * @param deadline 超时判定时间点
     * @param limit    单批最大返回条数，配显式 ORDER BY LAST_HEARTBEAT_TMS
     * @return 待置离线的设备行，按心跳时间由早到晚
     */
    List<F2fDeviceStatus> selectHeartbeatTimeout(@Param("deadline") LocalDateTime deadline,
                                                @Param("limit") int limit);

    /**
     * 把单台设备置为离线。仅当前为在线才更新，靠影响行数识别是否由本次调用改写。
     *
     * @return 影响行数；0 表示该设备不存在或已是离线
     */
    int markOfflineByDevice(@Param("channel") String channel,
                           @Param("deviceId") String deviceId);

    /**
     * 批量把心跳超时的在线设备置为离线，配合 {@link #selectHeartbeatTimeout} 使用。
     *
     * <p>WHERE 同时带 {@code LAST_HEARTBEAT_TMS &lt; deadline} 与 {@code ONLINE_FLAG = '1'}，
     * 因此扫表与置离线之间若设备恰好补上心跳，该行不会被误置离线。
     *
     * @param deadline 与扫表时使用的同一超时判定时间点
     * @return 实际被置为离线的行数
     */
    int markOfflineByDeadline(@Param("deadline") LocalDateTime deadline);

    /**
     * 按渠道列出设备最新状态，供运营端查看设备在线情况。
     *
     * @param channel    受理渠道
     * @param onlineFlag 在线标志过滤，传 null 或空串表示不过滤
     * @return 该渠道下的设备行，按最近心跳时间由新到旧
     */
    List<F2fDeviceStatus> selectByChannel(@Param("channel") String channel,
                                         @Param("onlineFlag") String onlineFlag);
}
