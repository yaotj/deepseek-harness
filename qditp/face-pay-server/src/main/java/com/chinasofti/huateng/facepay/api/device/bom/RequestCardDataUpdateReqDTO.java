package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** IF5A-03 票卡更新入参。 */
public class RequestCardDataUpdateReqDTO extends BaseDeviceRequest {

    private String cardId;

    private String updateType;

    private String adviceOpt;

    /** 操作员号，拼写照搬（少一个 t）。 */
    private String operaterId;

    private String updateStationCode;

    private String optDate;

    private String transAmount;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getUpdateType() {
        return updateType;
    }

    public void setUpdateType(String updateType) {
        this.updateType = updateType;
    }

    public String getAdviceOpt() {
        return adviceOpt;
    }

    public void setAdviceOpt(String adviceOpt) {
        this.adviceOpt = adviceOpt;
    }

    public String getOperaterId() {
        return operaterId;
    }

    public void setOperaterId(String operaterId) {
        this.operaterId = operaterId;
    }

    public String getUpdateStationCode() {
        return updateStationCode;
    }

    public void setUpdateStationCode(String updateStationCode) {
        this.updateStationCode = updateStationCode;
    }

    public String getOptDate() {
        return optDate;
    }

    public void setOptDate(String optDate) {
        this.optDate = optDate;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    @Override
    public String toString() {
        return "RequestCardDataUpdateReqDTO{cardId=" + cardId
                + ", updateType=" + updateType
                + ", adviceOpt=" + adviceOpt
                + ", operaterId=" + operaterId
                + ", updateStationCode=" + updateStationCode
                + ", optDate=" + optDate
                + ", transAmount=" + transAmount
                + ", deviceId=" + getDeviceId() + '}';
    }
}
