package com.chinasofti.huateng.model.app;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 黑名单异步导出 DTO。
 */
public class BlacklistExportDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String cardId;
    private String thirdUserId;
    private String status;
    private String channelSyncStatus;
    private String reason;
    private String releaseReason;
    private LocalDateTime createTime;

    public String getCardId()
    {
        return cardId;
    }

    public void setCardId(String cardId)
    {
        this.cardId = cardId;
    }

    public String getThirdUserId()
    {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId)
    {
        this.thirdUserId = thirdUserId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getChannelSyncStatus()
    {
        return channelSyncStatus;
    }

    public void setChannelSyncStatus(String channelSyncStatus)
    {
        this.channelSyncStatus = channelSyncStatus;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getReleaseReason()
    {
        return releaseReason;
    }

    public void setReleaseReason(String releaseReason)
    {
        this.releaseReason = releaseReason;
    }

    public LocalDateTime getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime)
    {
        this.createTime = createTime;
    }
}
