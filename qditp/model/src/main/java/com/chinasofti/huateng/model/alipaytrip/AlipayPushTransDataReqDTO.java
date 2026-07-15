package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行-行程数据推送请求参数（3.74 接口）。
 */
public class AlipayPushTransDataReqDTO {

    /**
     * 逻辑卡号
     */
    private String logicCard;

    /**
     * 交易类型  "01"：进站  "02"：出站  "03"：超时出站
     */
    private String transType;

    /**
     * 交易时间  yyyy-MM-dd HH:mm:ss
     */
    private String transTime;

    /**
     * 交易序列号
     */
    private String transSeq;

    /**
     * 交易车站代码
     */
    private String transStation;

    /**
     * 交易线路代码
     */
    private String transLine;

    /**
     * 交易记录id
     */
    private String tirpNo;

    /**
     * 第三方用户ID
     */
    private String thirdUserId;

    /**
     * 逻辑卡号
     */
    private String cardId;

    /**
     * 卡类型
     */
    private String cardType;

    /**
     * 签名类型
     */
    private String signType;

    /**
     * 签名
     */
    private String sign;

    public String getLogicCard() {
        return logicCard;
    }

    public void setLogicCard(String logicCard) {
        this.logicCard = logicCard;
    }

    public String getTransType() {
        return transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }

    public String getTransTime() {
        return transTime;
    }

    public void setTransTime(String transTime) {
        this.transTime = transTime;
    }

    public String getTransSeq() {
        return transSeq;
    }

    public void setTransSeq(String transSeq) {
        this.transSeq = transSeq;
    }

    public String getTransStation() {
        return transStation;
    }

    public void setTransStation(String transStation) {
        this.transStation = transStation;
    }

    public String getTransLine() {
        return transLine;
    }

    public void setTransLine(String transLine) {
        this.transLine = transLine;
    }

    public String getTirpNo() {
        return tirpNo;
    }

    public void setTirpNo(String tirpNo) {
        this.tirpNo = tirpNo;
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
