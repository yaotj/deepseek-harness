package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/** 请求票卡分析请求DTO。 */
public class RequestCardDataAnalyseReqDTO extends BaseRequestDTO {

    /** 发行方代码。 */
    private String providerId;

    /** 手机号，预留。 */
    private String msisdn;

    /** 逻辑卡号。 */
    private String cardId;

    /** 更新区域类型。 */
    private String updateType;

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

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
        return "RequestCardDataAnalyseReqDTO{" +
                "providerId='" + providerId + '\'' +
                ", msisdn='" + msisdn + '\'' +
                ", cardId='" + cardId + '\'' +
                ", updateType='" + updateType + '\'' +
                ", deviceId='" + getDeviceId() + '\'' +
                '}';
    }
}
