package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF5A-01 票卡分析入参。本接口是<b>纯透传</b>，ITP 只做参数校验与审计留痕，
 * 分析逻辑在 ticket-server。
 *
 * <p><b>{@code providerId} 不在此重复声明</b>：父类 {@link BaseDeviceRequest} 已有该字段。
 * 旧 DTO 在子类又声明了一遍，导致 {@code TransforUtils.copyBaseParams} 回填公共参数时
 * 用表单里的（往往为空的）{@code providerId} 覆盖掉 bizData 里已解析的值。</p>
 */
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
