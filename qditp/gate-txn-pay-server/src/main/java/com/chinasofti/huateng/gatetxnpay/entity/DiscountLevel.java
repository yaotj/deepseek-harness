package com.chinasofti.huateng.gatetxnpay.entity;

import java.math.BigDecimal;

/** DISCOUNT_LEVEL 折扣档位。 */
public class DiscountLevel {
    private Integer levelAmt;
    private BigDecimal levelDiscount;
    private String discountType;
    private String discountStatus;

    public Integer getLevelAmt() { return levelAmt; }
    public void setLevelAmt(Integer levelAmt) { this.levelAmt = levelAmt; }
    public BigDecimal getLevelDiscount() { return levelDiscount; }
    public void setLevelDiscount(BigDecimal levelDiscount) { this.levelDiscount = levelDiscount; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public String getDiscountStatus() { return discountStatus; }
    public void setDiscountStatus(String discountStatus) { this.discountStatus = discountStatus; }
}
