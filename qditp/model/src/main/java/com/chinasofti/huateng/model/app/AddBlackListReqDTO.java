package com.chinasofti.huateng.model.app;


public class AddBlackListReqDTO {

    private String cardId;


    private String thirdUserId;


    private String cardType;


    private String channelCode;


    private String blackSource;


    private String blackCause;


    private String bizNo;


    private String reason;


    private String createBy;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public String getBlackSource() {
        return blackSource;
    }

    public void setBlackSource(String blackSource) {
        this.blackSource = blackSource;
    }

    public String getBlackCause() {
        return blackCause;
    }

    public void setBlackCause(String blackCause) {
        this.blackCause = blackCause;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    @Override
    public String toString() {
        return "AddBlackListReqDTO{cardId='" + cardId + "', thirdUserId='" + thirdUserId
                + "', cardType='" + cardType + "', channelCode='" + channelCode
                + "', blackSource='" + blackSource + "', blackCause='" + blackCause
                + "', bizNo='" + bizNo + "', reason='" + reason + "', createBy='" + createBy + "'}";
    }
}
