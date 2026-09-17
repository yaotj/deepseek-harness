package com.chinasofti.huateng.model.app;

/**
 * 更新 HCE 卡数据请求。
 */
public class UpdateHceDataReqDTO {
    /**
     * HCE 逻辑卡号。
     */
    private String cardId;

    /**
     * 闸机处理后的 HCE 卡数据，来源于 IF1A-01 的 {@code reserve1}。
     */
    private String hceData;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getHceData() {
        return hceData;
    }

    public void setHceData(String hceData) {
        this.hceData = hceData;
    }

    @Override
    public String toString() {
        return "UpdateHceDataReqDTO{cardId='" + cardId + "', hceData='" + hceData + "'}";
    }
}
