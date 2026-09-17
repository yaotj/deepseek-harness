package com.chinasofti.huateng.model.cardpool;

/** Internal response returned after a logical card number has been reserved. */
public class CardPoolReservationRespDTO {
    private String reservationId;
    private String cardNo;
    private String cardType;
    private String expireTime;

    /**
     * 取预占记录标识。
     * @return 预占记录标识，后续 confirm / release 必须回传该值定位本次预占。
     */
    public String getReservationId() { return reservationId; }

    /**
     * 设置预占记录标识。
     * @param reservationId 预占记录标识，由 card-pool-server 生成，供后续 confirm / release 引用。
     */
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    /**
     * 取本次预占到的逻辑卡号。
     * @return 逻辑卡号；池内该票种无可用卡号时为空。
     */
    public String getCardNo() { return cardNo; }

    /**
     * 设置本次预占到的逻辑卡号。
     * @param cardNo 逻辑卡号；池内该票种无可用卡号时置空。
     */
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }

    /**
     * 取该卡号所。
     * @return 票种码，044X 形式，与请求中的票种一致。
     */
    public String getCardType() { return cardType; }

    /**
     * 设置该卡号所。
     * @param cardType 票种码，044X 形式，与请求中的票种一致。
     */
    public void setCardType(String cardType) { this.cardType = cardType; }

    /**
     * 取预占过期时间。
     * @return 预占过期时间；过期后未 confirm 的卡号由卡号池回收为 AVAILABLE。
     */
    public String getExpireTime() { return expireTime; }

    /**
     * 设置预占过期时间。
     * @param expireTime 预占过期时间；过期后未 confirm 的卡号由卡号池回收为 AVAILABLE。
     */
    public void setExpireTime(String expireTime) { this.expireTime = expireTime; }
}
