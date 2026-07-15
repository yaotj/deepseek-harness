package com.chinasofti.huateng.model.app.dailyticket;

/**
 * IF8A-71 通知ACC车票已使用请求参数。
 */
public class DailyTicketUsedNoticeReqDTO {
    /**
     * 日票虚拟卡号。
     */
    private String cardNum;

    /**
     * 有效期天数。
     */
    private Integer period;

    /**
     * 计次/计时结束时间，毫秒时间戳。
     */
    private Long countingEnd;

    /**
     * 优惠金额，单位分。
     */
    private Integer discountAmount;

    /**
     * 车票名称。
     */
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
}
