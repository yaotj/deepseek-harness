package com.chinasofti.huateng.model.app;

/**
 * 按签约流水号查询支付通道应答（account-server 内部只读接口）。
 */
public class QueryPayChannelByContractResult {

    private String retCode;

    private String retMsg;

    private String thirdUserId;

    private String cardId;

    /** 支付通道表里的卡类型，取值口径与 {@code USER_ITP_REG_INFO.CARD_TYPE} 一致（如 0441）。 */
    private String cardType;

    private String channel;

    private String thirdPayId;

    private String reqContractNo;

    private String status;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getThirdPayId() {
        return thirdPayId;
    }

    public void setThirdPayId(String thirdPayId) {
        this.thirdPayId = thirdPayId;
    }

    public String getReqContractNo() {
        return reqContractNo;
    }

    public void setReqContractNo(String reqContractNo) {
        this.reqContractNo = reqContractNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "QueryPayChannelByContractResult{retCode='" + retCode
                + "', retMsg='" + retMsg
                + "', thirdUserId='" + thirdUserId
                + "', cardId='" + cardId
                + "', cardType='" + cardType
                + "', channel='" + channel
                + "', reqContractNo='" + reqContractNo
                + "', status='" + status + "'}";
    }
}
