package com.chinasofti.huateng.facepay.api.page;

/**
 * 设备心跳状态汇总，供运营端页面顶部统计卡使用。
 *
 * <p>刻意做成全表口径、不受查询条件影响：运维要的是一眼看清「一共多少台、几台掉线」，
 * 若跟着筛选条件变，看到 1 台离线就分不清是「真的只掉 1 台」还是「筛出来的只有 1 台」。
 */
public class F2fDeviceStatusSummaryVO {

    /** 设备总数，即 {@code F2F_DEVICE_STATUS} 的行数。 */
    private Long total;

    /** 在线设备数（{@code ONLINE_FLAG = '1'}）。 */
    private Long onlineCount;

    /** 离线设备数（{@code ONLINE_FLAG = '0'}）。 */
    private Long offlineCount;

    /** 全表最近一次心跳时间，格式 {@code YYYY-MM-DD HH24:MI:SS}；表为空时为 null。 */
    private String lastHeartbeatTms;

    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }

    public Long getOnlineCount() { return onlineCount; }
    public void setOnlineCount(Long onlineCount) { this.onlineCount = onlineCount; }

    public Long getOfflineCount() { return offlineCount; }
    public void setOfflineCount(Long offlineCount) { this.offlineCount = offlineCount; }

    public String getLastHeartbeatTms() { return lastHeartbeatTms; }
    public void setLastHeartbeatTms(String lastHeartbeatTms) { this.lastHeartbeatTms = lastHeartbeatTms; }
}
