package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** IF5A-01 票卡分析入参。 */
public class RequestCardDataAnalyseReqDTO extends BaseDeviceRequest {

    private String msisdn;

    private String cardId;

    private String updateType;

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

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

    @Override
    public String toString() {
        return "RequestCardDataAnalyseReqDTO{cardId=" + cardId
                + ", updateType=" + updateType
                + ", msisdn=" + msisdn
                + ", providerId=" + getProviderId()
                + ", deviceId=" + getDeviceId() + '}';
    }
}
