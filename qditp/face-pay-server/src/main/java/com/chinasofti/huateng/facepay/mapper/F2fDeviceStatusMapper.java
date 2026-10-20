package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.api.page.F2fDeviceStatusPageVO;
import com.chinasofti.huateng.facepay.api.page.F2fDeviceStatusSummaryVO;
import com.chinasofti.huateng.facepay.entity.F2fDeviceStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 设备心跳状态 Mapper（表 F2F_DEVICE_STATUS）。 */
@Mapper
public interface F2fDeviceStatusMapper {

    /**
     * 心跳上报 upsert：单条 MERGE INTO 完成「有则更新、无则插入」。
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
     * 扫出心跳超时但仍标记在线的设备，供置离线任务使用。
     *
     * @param deadline 超时判定时间点
     * @param limit    单批最大返回条数，配显式 ORDER BY LAST_HEARTBEAT_TMS
     * @return 待置离线的设备行，按心跳时间由早到晚
     */
    List<F2fDeviceStatus> selectHeartbeatTimeout(@Param("deadline") LocalDateTime deadline,
                                                @Param("limit") int limit);

    /**
     * 把单台设备置为离线。
     *
     * @return 影响行数；0 表示该设备不存在或已是离线
     */
    int markOfflineByDevice(@Param("channel") String channel,
                           @Param("deviceId") String deviceId);

    /**
     * 批量把心跳超时的在线设备置为离线，配合 {@link #selectHeartbeatTimeout} 使用。
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

    /**
     * 分页查询设备最新心跳状态，供运营端「设备心跳状态」页使用。
     *
     * <p>排序固定为「离线优先 + 最近心跳倒序」（{@code ONLINE_FLAG ASC, LAST_HEARTBEAT_TMS DESC}），
     * 运维打开页面第一屏就该是掉线设备；DEVICE_ID 兜底保证翻页稳定不重不漏。
     *
     * @param channel    受理渠道过滤，null 或空串表示不过滤
     * @param onlineFlag 在线标志过滤，null 或空串表示不过滤
     * @param deviceId   设备号模糊匹配关键字，<b>已由调用方转义</b> {@code % _ \}，null 或空串表示不过滤
     * @param offset     偏移量
     * @param limit      单页条数
     * @return 当前页数据；{@code stationName} 关联不到时为 null
     */
    List<F2fDeviceStatusPageVO> selectDevicePage(@Param("channel") String channel,
                                                @Param("onlineFlag") String onlineFlag,
                                                @Param("deviceId") String deviceId,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    /**
     * 统计 {@link #selectDevicePage} 的命中总数，过滤条件 MUST 与之一致。
     *
     * @return 命中行数
     */
    long countDevicePage(@Param("channel") String channel,
                         @Param("onlineFlag") String onlineFlag,
                         @Param("deviceId") String deviceId);

    /**
     * 全表汇总：设备总数、在线数、离线数、最近一次心跳时间。
     *
     * <p>刻意不接受过滤条件，口径见 {@link F2fDeviceStatusSummaryVO}。
     *
     * @return 恒返回一行；表为空时各计数为 0、时间戳为 null
     */
    F2fDeviceStatusSummaryVO selectDeviceSummary();
}
