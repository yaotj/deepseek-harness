package com.chinasofti.huateng.model.app;

/**
 * IF8B-02 APP 解约结果通知业务参数。
 */
public class AppTerminationResultNotifyReqDTO {
    /** 三方用户 ID。 */
    private String thirdUserId;
    /** 卡 ID。 */
    private String cardId;
    /** 卡类型。 */
    private String cardType;
    /** 商户端签约流水号。 */
    private String requestSignSeq;
    /** 解约结果，SUCCESS 表示解约成功，FAIL 表示解约失败。 */
    private String terminationResult;
    /** 解约结果说明。 */
    private String terminationResultMsg;
    /** 解约时间，格式 yyyyMMddHHmmss。 */
    private String terminationTime;

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

    public String getRequestSignSeq() {
        return requestSignSeq;
    }

    public void setRequestSignSeq(String requestSignSeq) {
        this.requestSignSeq = requestSignSeq;
    }

    public String getTerminationResult() {
        return terminationResult;
    }

    public void setTerminationResult(String terminationResult) {
        this.terminationResult = terminationResult;
    }

    public String getTerminationResultMsg() {
        return terminationResultMsg;
    }

    public void setTerminationResultMsg(String terminationResultMsg) {
        this.terminationResultMsg = terminationResultMsg;
    }

    public String getTerminationTime() {
        return terminationTime;
    }

    public void setTerminationTime(String terminationTime) {
        this.terminationTime = terminationTime;
    }
}
