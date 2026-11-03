package com.chinasofti.huateng.model.alipay;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付宝用户异步导出 DTO。
 */
public class AlipayUserExportDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String msisdn;
    private String channel;
    private String thirdPayId;
    private String status;
    private LocalDateTime createTime;

    public String getThirdUserId()
    {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId)
    {
        this.thirdUserId = thirdUserId;
    }

    public String getCardId()
    {
        return cardId;
    }

    public void setCardId(String cardId)
    {
        this.cardId = cardId;
    }

    public String getCardType()
    {
        return cardType;
    }

    public void setCardType(String cardType)
    {
        this.cardType = cardType;
    }

    public String getMsisdn()
    {
        return msisdn;
    }

    public void setMsisdn(String msisdn)
    {
        this.msisdn = msisdn;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getThirdPayId()
    {
        return thirdPayId;
    }

    public void setThirdPayId(String thirdPayId)
    {
        this.thirdPayId = thirdPayId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
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
