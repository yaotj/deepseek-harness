package com.chinasofti.huateng.para.entity.risk;

/**
 * 滞留进站事件行（QRCODE_STATUS 仍在 04 已进站且 GATE_IN_TIME 早于阈值时点）。
 */
public class StuckEntryEvent {
    private String cardId;
    private String gateInTime;
    private String gateInStation;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getGateInTime() {
        return gateInTime;
    }

    public void setGateInTime(String gateInTime) {
        this.gateInTime = gateInTime;
    }

    public String getGateInStation() {
        return gateInStation;
    }

    public void setGateInStation(String gateInStation) {
        this.gateInStation = gateInStation;
    }
}
