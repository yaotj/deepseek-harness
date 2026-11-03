package com.chinasofti.huateng.model.pay;

import java.io.Serializable;

/**
 * 离线码交易统计异步导出 DTO。
 */
public class OfflineCodeStatExportDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String stationCode;
    private String stationName;
    private Long txnCount;
    private Long cardCount;

    public String getStationCode()
    {
        return stationCode;
    }

    public void setStationCode(String stationCode)
    {
        this.stationCode = stationCode;
    }

    public String getStationName()
    {
        return stationName;
    }

    public void setStationName(String stationName)
    {
        this.stationName = stationName;
    }

    public Long getTxnCount()
    {
        return txnCount;
    }

    public void setTxnCount(Long txnCount)
    {
        this.txnCount = txnCount;
    }

    public Long getCardCount()
    {
        return cardCount;
    }

    public void setCardCount(Long cardCount)
    {
        this.cardCount = cardCount;
    }
}
