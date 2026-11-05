package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

public class RequestGenNoCashOrderReqDTO extends BaseDeviceRequest {

    private String transType;

    private String adminTransType;

    private String operaterId;

    private String shiftId;

    private String cardId;

    private String transAount;

    private String bomOptSeq;

    public Long amountInFen() {
        if (transAount == null || transAount.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(transAount.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getTransType() {
        return transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }

    public String getAdminTransType() {
        return adminTransType;
    }

    public void setAdminTransType(String adminTransType) {
        this.adminTransType = adminTransType;
    }

    public String getOperaterId() {
        return operaterId;
    }

    public void setOperaterId(String operaterId) {
        this.operaterId = operaterId;
    }

    public String getShiftId() {
        return shiftId;
    }

    public void setShiftId(String shiftId) {
        this.shiftId = shiftId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getTransAount() {
        return transAount;
    }

    public void setTransAount(String transAount) {
        this.transAount = transAount;
    }

    public String getBomOptSeq() {
        return bomOptSeq;
    }

    public void setBomOptSeq(String bomOptSeq) {
        this.bomOptSeq = bomOptSeq;
    }

    @Override
    public String toString() {
        return "RequestGenNoCashOrderReqDTO{transType=" + transType
                + ", adminTransType=" + adminTransType
                + ", operaterId=" + operaterId
                + ", shiftId=" + shiftId
                + ", cardId=" + cardId
                + ", transAount=" + transAount
                + ", bomOptSeq=" + bomOptSeq
                + ", deviceId=" + getDeviceId() + '}';
    }
}
