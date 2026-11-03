package com.chinasofti.huateng.model.app;

import java.io.Serializable;

/**
 * 注册量统计异步导出 DTO。
 */
public class RegStatExportDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String cardType;
    private String cardTypeName;
    private Long regCount;

    public String getCardType()
    {
        return cardType;
    }

    public void setCardType(String cardType)
    {
        this.cardType = cardType;
    }

    public String getCardTypeName()
    {
        return cardTypeName;
    }

    public void setCardTypeName(String cardTypeName)
    {
        this.cardTypeName = cardTypeName;
    }

    public Long getRegCount()
    {
        return regCount;
    }

    public void setRegCount(Long regCount)
    {
        this.regCount = regCount;
    }
}
