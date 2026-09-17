package com.chinasofti.huateng.account.page;

import java.time.LocalDateTime;

/**
 * APP_TERMINATION_REQUEST 的解约时间聚合投影，仅供「用户查询」运营页展示。
 */
public class TerminationTimeSummary {
    /**
     * 逻辑卡号。
     */
    private String cardId;
    /**
     * 第三方用户编码。
     */
    private String thirdUserId;
    /**
     * 最近一次申请解绑时间（MAX(REQUEST_TIME)）。
     */
    private LocalDateTime requestTime;
    /**
     * 最近一次解绑成功时间（仅统计 TERMINATION_STATUS = 'SUCCESS' 的 MAX(COMPLETE_TIME)）。
     */
    private LocalDateTime completeTime;

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

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }

    public LocalDateTime getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(LocalDateTime completeTime) {
        this.completeTime = completeTime;
    }
}
