package com.chinasofti.huateng.model.app;


public class TripDataDTO {
    private String totalPrice;
    private String totalDebit;
    private String totalDiscount;
    private String totalOvertime;
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
