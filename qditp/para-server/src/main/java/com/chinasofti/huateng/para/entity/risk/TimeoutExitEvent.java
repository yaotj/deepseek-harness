package com.chinasofti.huateng.para.entity.risk;

/**
 * 超时出站事件行（QRCODE_TXN_DETAIL 出站明细且 OVERTIME_AMOUNT 大于 0，即系统确认的一笔单边）。
 */
public class TimeoutExitEvent {
    private String cardId;
    private String deviceId;
    private String handleDateTime;
    private String ticketTransSeq;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getHandleDateTime() {
        return handleDateTime;
    }

    public void setHandleDateTime(String handleDateTime) {
        this.handleDateTime = handleDateTime;
    }

    public String getTicketTransSeq() {
        return ticketTransSeq;
    }

    public void setTicketTransSeq(String ticketTransSeq) {
        this.ticketTransSeq = ticketTransSeq;
    }
}
