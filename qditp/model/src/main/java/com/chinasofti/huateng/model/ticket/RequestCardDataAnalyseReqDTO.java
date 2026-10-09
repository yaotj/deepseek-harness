package com.chinasofti.huateng.model.ticket;

/**
 * 请求票卡分析请求DTO。
 */
public class RequestCardDataAnalyseReqDTO {

    private String providerId;
    private String msisdn;
    private String cardId;
    private String updateType;

    /**
     * 操作员编码。
     */
    private String managerCode;

    /**
     * BOM 设备所属站码（由 face-pay 按 deviceId 前 4 位推导后带入，仅用于 IF5A-01 同站/跨站判定）。
     * 非对外契约字段，face-pay 与 ticket-server 之间的内部 RPC 入参；BOM 设备报文本身不含此值。
     */
    private String bomStationCode;

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

    public String getManagerCode() {
        return managerCode;
    }

    public void setManagerCode(String managerCode) {
        this.managerCode = managerCode;
    }

    public String getBomStationCode() {
        return bomStationCode;
    }

    public void setBomStationCode(String bomStationCode) {
        this.bomStationCode = bomStationCode;
    }
}
