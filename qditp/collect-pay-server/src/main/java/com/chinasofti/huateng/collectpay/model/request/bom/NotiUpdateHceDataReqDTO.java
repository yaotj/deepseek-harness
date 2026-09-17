package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/** IF5A-09 HCE票卡更新结果通知请求DTO。 */
public class NotiUpdateHceDataReqDTO extends BaseRequestDTO {

    /** 更新区域类型。 */
    private String updateType;

    /** 建议本次操作类型。 */
    private String adviceOpt;

    /** 操作员编码。 */
    private String operaterId;

    /** 逻辑卡号。 */
    private String cardId;

    /** 补站站点。 */
    private String updateStationCode;

    /** 更新时间。 */
    private String optDate;

    /** 交易金额。 */
    private String transAmount;

    /** HCE卡数据（16进制字符串）。 */
    private String hceData;

    /** 交易序列号。 */
    private String tikcetTransSeq;

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

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
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

    public String getHceData() {
        return hceData;
    }

    public void setHceData(String hceData) {
        this.hceData = hceData;
    }

    public String getTikcetTransSeq() {
        return tikcetTransSeq;
    }

    public void setTikcetTransSeq(String tikcetTransSeq) {
        this.tikcetTransSeq = tikcetTransSeq;
    }

    @Override
    public String toString() {
        return "NotiUpdateHceDataReqDTO{" +
                "updateType='" + updateType + '\'' +
                ", adviceOpt='" + adviceOpt + '\'' +
                ", operaterId='" + operaterId + '\'' +
                ", cardId='" + cardId + '\'' +
                ", updateStationCode='" + updateStationCode + '\'' +
                ", optDate='" + optDate + '\'' +
                ", transAmount='" + transAmount + '\'' +
                ", hceData='" + hceData + '\'' +
                ", tikcetTransSeq='" + tikcetTransSeq + '\'' +
                ", providerId='" + getProviderId() + '\'' +
                ", deviceId='" + getDeviceId() + '\'' +
                '}';
    }
}
