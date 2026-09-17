package com.chinasofti.huateng.model.app;

/**
 * IF8A-41 查询账单统计应答中的统计数据对象。
 */
public class TripDataDTO {
    /** 总金额（元）：地铁原价合计，原价缺失时退化为票价。 */
    private String totalPrice;
    /** 总支付（元）：实付合计，含超时加收。 */
    private String totalDebit;
    /** 总优惠（元）：原价 - 票价，逐笔下取 0。 */
    private String totalDiscount;
    /** 总超时费（元）：超时加收合计，与优惠是相反方向的量。 */
    private String totalOvertime;
    /** 订单数量。 */
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

    public String getTotalOvertime() {
        return totalOvertime;
    }

    public void setTotalOvertime(String totalOvertime) {
        this.totalOvertime = totalOvertime;
    }

    @Override
    public String toString() {
        return "TripDataDTO{totalPrice='" + totalPrice + "', totalDebit='" + totalDebit
                + "', totalDiscount='" + totalDiscount + "', totalOvertime='" + totalOvertime
                + "', count=" + count + "}";
    }
}
