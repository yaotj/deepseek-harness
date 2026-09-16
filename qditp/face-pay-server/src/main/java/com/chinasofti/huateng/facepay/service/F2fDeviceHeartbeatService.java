package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.mapper.F2fDeviceStatusMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 设备心跳。TVM 与 BOM 的 {@code notiDeviceHeard} 共用本服务，只有渠道码不同。
 *
 * <p><b>补上的旧缺口</b>：旧实现的 {@code notiDeviceHeard} 在 controller 里直接
 * {@code return TvmOrderResult.success()}——<b>不落库、不校验设备、不记时间</b>，
 * 对任何请求恒答成功，心跳完全没有监控价值（枚举里定义了 {@code 2001 非法设备} 却无人使用）。
 * 本实现把心跳落到 {@code F2F_DEVICE_STATUS}，配合
 * {@code F2fDeviceOfflineJob} 做超时置离线。</p>
 *
 * <p><b>为什么必须用 MERGE INTO</b>：主键是 {@code (CHANNEL, DEVICE_ID)}，一台设备一行。
 * 心跳是高频并发写（每设备约 1 分钟一次，全线设备数百台），
 * 「先 SELECT 判断存在再决定 insert / update」在同一设备重连或多线程重投时会撞主键。
 * 单条 MERGE 由数据库保证原子性，这也是本项目的既有约定（AGENTS.md §2.2.1）。</p>
 *
 * <p>不带 {@code @Transactional}：单条 SQL，自动提交即可。</p>
 */
@Service
public class F2fDeviceHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(F2fDeviceHeartbeatService.class);

    private final F2fDeviceStatusMapper deviceStatusMapper;

    public F2fDeviceHeartbeatService(F2fDeviceStatusMapper deviceStatusMapper) {
        this.deviceStatusMapper = deviceStatusMapper;
    }

    /**
     * 记一次心跳。
     *
     * <p><b>{@code deviceId} 为空时只记日志、不落库，但仍要让调用方回成功</b>——
     * 心跳接口的契约是恒回 {@code 0000}，改成失败会让设备侧告警刷屏，
     * 而设备号缺失属于对端报文问题，不是本服务能修的。</p>
     *
     * @param channel     渠道码，见 {@code F2fChannel}
     * @param deviceId    设备号，可能为空
     * @param stationCode 车站编码，可空；非空时覆盖已有值
     * @return true 表示已落库，false 表示因设备号缺失被跳过
     */
    public boolean recordHeartbeat(String channel, String deviceId, String stationCode) {
        if (deviceId == null || deviceId.isBlank()) {
            log.warn("设备心跳缺少 deviceId，跳过落库, channel={}", channel);
            return false;
        }
        int merged = deviceStatusMapper.mergeHeartbeat(channel, deviceId, stationCode, LocalDateTime.now());
        log.debug("设备心跳已记录, channel={}, deviceId={}, mergedRows={}", channel, deviceId, merged);
        return merged > 0;
    }

    /**
     * 把心跳超时的在线设备批量置离线，供 {@code @Scheduled} 调用。
     *
     * <p>判定窗口由调用方按「心跳周期 × 容忍倍数」算好后传入，SQL 内不做时间计算。
     * WHERE 同时带 {@code ONLINE_FLAG='1'}，因此重复执行不会把已离线的行再改一遍。</p>
     *
     * @return 实际被置离线的设备数
     */
    public int markTimeoutDevicesOffline(LocalDateTime deadline) {
        int offline = deviceStatusMapper.markOfflineByDeadline(deadline);
        if (offline > 0) {
            log.warn("心跳超时置离线设备 {} 台, deadline={}", offline, deadline);
        }
        return offline;
    }
}
