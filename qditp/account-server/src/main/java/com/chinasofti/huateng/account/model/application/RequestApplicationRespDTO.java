package com.chinasofti.huateng.account.model.application;

/**
 * IF8A-01 请求开户响应报文。
 */
public class RequestApplicationRespDTO {
    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

    /**
     * 开户成功后返回的逻辑卡号。
     */
    private String cardId;

    /**
     * 开户成功后的卡类型。
     */
    private String cardType;

    /**
     * 签名类型。
     */
    private String signType;

    /**
     * 响应签名值。
     */
    private String sign;

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

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }
}
