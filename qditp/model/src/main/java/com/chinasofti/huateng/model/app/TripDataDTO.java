package com.chinasofti.huateng.model.app;

/**
 * IF8A-41 查询账单统计应答中的统计数据对象。
 */
public class TripDataDTO {
    /** 总金额（元） */
    private String totalPrice;
    /** 总支付（元） */
    private String totalDebit;
    /** 总优惠（元） */
    private String totalDiscount;
    /** 订单数量 */
    private Integer count;

    public String getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(String totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getTotalDebit() {
        return totalDebit;
    }

    public void setTotalDebit(String totalDebit) {
        this.totalDebit = totalDebit;
    }

    public String getTotalDiscount() {
        return totalDiscount;
    }

    public void setTotalDiscount(String totalDiscount) {
        this.totalDiscount = totalDiscount;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    @Override
    public String toString() {
        return "TripDataDTO{totalPrice='" + totalPrice + "', totalDebit='" + totalDebit
                + "', totalDiscount='" + totalDiscount + "', count=" + count + "}";
    }
}
