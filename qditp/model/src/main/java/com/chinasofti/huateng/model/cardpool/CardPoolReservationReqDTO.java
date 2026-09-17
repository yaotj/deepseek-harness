package com.chinasofti.huateng.model.cardpool;

/** Internal request for reserving one logical card number. */
public class CardPoolReservationReqDTO {
    private String cardType;
    private String businessType;
    private String businessId;
    private String ownerId;

    /**
     * 取本次预占请求的票种码。
     * @return 票种码，044X 形式；决定从哪个票种的逻辑卡号池中取号。
     */
    public String getCardType() { return cardType; }

    /**
     * 设置本次预占请求的票种码。
     * @param cardType 票种码，044X 形式；card-pool-server 按该票种筛选可用卡号。
     */
    public void setCardType(String cardType) { this.cardType = cardType; }

    /**
     * 取发起预占的业务类型。
     * @return 业务类型标识，用于区分开户、员工卡等不同用卡场景。
     */
    public String getBusinessType() { return businessType; }

    /**
     * 设置发起预占的业务类型。
     * @param businessType 业务类型标识，用于区分开户、员工卡等不同用卡场景。
     */
    public void setBusinessType(String businessType) { this.businessType = businessType; }

    /**
     * 取业务流水号。
     * @return 业务流水号，预占 / 确认 / 释放三步以此串联，同时作为幂等依据。
     */
    public String getBusinessId() { return businessId; }

    /**
     * 设置业务流水号。
     * @param businessId 业务流水号，预占 / 确认 / 释放三步以此串联，重复提交时用于幂等判重。
     */
    public void setBusinessId(String businessId) { this.businessId = businessId; }

    /**
     * 取卡号归。
     * @return 归。
     */
    public String getOwnerId() { return ownerId; }

    /**
     * 设置卡号归。
     * @param ownerId 归。
     */
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
}
