package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/**
 * BOM票卡更新请求DTO。
 */
public class RequestCardDataUpdateReqDTO extends BaseRequestDTO {

    /**
     * 逻辑卡号。
     */
    private String cardId;

    /**
     * 更新区域类型。
     * 00：非付费区
     * 01：付费区
     */
    private String updateType;

    /**
     * 建议本次操作类型。
     * 018：补进站 无法出站
     * 006：补出站（最低票价）无法进站
     * 005: 20分免费进站更新无法进站
     */
    private String adviceOpt;

    /**
     * 操作员编码。
     */
    private String operaterId;

    /**
     * 补站站点。
     */
    private String updateStationCode;

    /**
     * 更新时间。
     */
    private String optDate;

    /**
     * 交易金额。
     */
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
}
