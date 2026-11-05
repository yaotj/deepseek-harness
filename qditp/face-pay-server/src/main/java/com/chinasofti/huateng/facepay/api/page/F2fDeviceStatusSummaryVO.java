package com.chinasofti.huateng.facepay.api.page;

public class F2fDeviceStatusSummaryVO {

    private Long total;

    private Long onlineCount;

    private Long offlineCount;

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
