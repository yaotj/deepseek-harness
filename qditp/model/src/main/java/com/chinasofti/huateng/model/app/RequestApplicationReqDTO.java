package com.chinasofti.huateng.model.app;

/**
 * @author zzm
 * @date 2026/5/25 11:44
 */
public class RequestApplicationReqDTO {

    /**
     * 第三方用户ID
     */
    private String thirdUserId;

    /**
     * 支付账户ID  （签约回调）  默认空
     */
    private String thirdPayId;

    /**
     * 支付通道编码（默认空）
     */
    private String channel;

    /**
     * 第三方签约流水号
     */
    private String reqContractNo;

    /**
     * 卡类型编码
     */
    private String cardType;

    /**
     * 手机号
     */
    private String msisdn;

    /**
     * 姓名
     */
    private String userName;

    /**
     * 用户身份证号
     */
    private String userId;

    private String extend1;

    private String extend2;

    /**
     * NFC开卡使用字段 01 非钱包 02 钱包NFC卡  非必填
     */
    private String ticketCard;

    /**
     * 通行票是Y  第三方是C 目前两种表示对应的卡类型都是 0441 非必填
     */
    private String companionFlag;

    /**
     * 车票显示
     * 1.需要同一所属方限制开发数量为1
     * 2.可开多张，每次请求都给一张新卡
     */
    private String ticketLimit;

    /**
     * 票卡所属方，不固定，目前有地铁APP、支付宝出行、海上巴士、其他互通APP等，不需要校验是否存在
     */
    private String cardIssueCode;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getThirdPayId() {
        return thirdPayId;
    }

    public void setThirdPayId(String thirdPayId) {
        this.thirdPayId = thirdPayId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getReqContractNo() {
        return reqContractNo;
    }

    public void setReqContractNo(String reqContractNo) {
        this.reqContractNo = reqContractNo;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getExtend1() {
        return extend1;
    }

    public void setExtend1(String extend1) {
        this.extend1 = extend1;
    }

    public String getExtend2() {
        return extend2;
    }

    public void setExtend2(String extend2) {
        this.extend2 = extend2;
    }

    public String getTicketCard() {
        return ticketCard;
    }

    public void setTicketCard(String ticketCard) {
        this.ticketCard = ticketCard;
    }

    public String getCompanionFlag() {
        return companionFlag;
    }

    public void setCompanionFlag(String companionFlag) {
        this.companionFlag = companionFlag;
    }

    public String getTicketLimit() {
        return ticketLimit;
    }

    public void setTicketLimit(String ticketLimit) {
        this.ticketLimit = ticketLimit;
    }

    public String getCardIssueCode() {
        return cardIssueCode;
    }

    public void setCardIssueCode(String cardIssueCode) {
        this.cardIssueCode = cardIssueCode;
    }
}
