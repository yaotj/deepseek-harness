package com.chinasofti.huateng.account.page;

/**
 * 注册量统计运营展示对象（按票种分组）。
 */
public class RegStatView {
    /**
     * 真实卡类型编码（CARD_TYPE）。
     */
    private String cardType;
    /**
     * 卡类型中文名（由 CardTypeCodeEnum 解析，未知编码原样回退）。
     */
    private String cardTypeName;
    /**
     * 该票种注册量。
     */
    private Long regCount;

    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getCardTypeName() { return cardTypeName; }
    public void setCardTypeName(String cardTypeName) { this.cardTypeName = cardTypeName; }
    public Long getRegCount() { return regCount; }
    public void setRegCount(Long regCount) { this.regCount = regCount; }
}
