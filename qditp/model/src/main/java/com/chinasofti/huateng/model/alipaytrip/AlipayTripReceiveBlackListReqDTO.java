package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * 支付宝出行-黑名单状态变更通知请求参数。
 * 与APP黑名单状态变更通知参数相同。
 */
public class AlipayTripReceiveBlackListReqDTO {

    /**
     * 第三方用户ID
     */
    private String thirdUserId;

    /**
     * 卡片ID/逻辑卡号
     */
    private String cardId;

    /**
     * 黑名单状态
     */
    private String blacklistStatus;

    /**
     * 变更原因
     */
    private String reason;

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

    public String getBlacklistStatus() {
        return blacklistStatus;
    }

    public void setBlacklistStatus(String blacklistStatus) {
        this.blacklistStatus = blacklistStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
