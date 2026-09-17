package com.chinasofti.huateng.ticket.model.page;

/** 用户运营端人工调整乘车状态请求。 */
public class RideStatusUpdateRequest {
    private String codeStatus;
    private String changeReason;

    public String getCodeStatus() {
        return codeStatus;
    }

    public void setCodeStatus(String codeStatus) {
        this.codeStatus = codeStatus;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }
}
