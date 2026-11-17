package com.chinasofti.huateng.model.app.dailyticket;


public class DailyTicketUsedNoticeReqDTO {

    private String cardNum;


    private Integer period;


    private Long countingEnd;


    private Integer discountAmount;


    private String ticketName;

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public Integer getPeriod() {
        return period;
    }

    public void setPeriod(Integer period) {
        this.period = period;
    }

    public Long getCountingEnd() {
        return countingEnd;
    }

    public void setCountingEnd(Long countingEnd) {
        this.countingEnd = countingEnd;
    }

    public Integer getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(Integer discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getTicketName() {
        return ticketName;
    }

    public void setTicketName(String ticketName) {
        this.ticketName = ticketName;
    }

    @Override
    public String toString() {
        return "DailyTicketUsedNoticeReqDTO{cardNum='" + cardNum + "', period=" + period
                + ", countingEnd=" + countingEnd + ", discountAmount=" + discountAmount
                + ", ticketName='" + ticketName + "'}";
    }
}
