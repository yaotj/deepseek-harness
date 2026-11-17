package com.chinasofti.huateng.blacklist.entity;

import java.time.LocalDateTime;

public class GraylistOperateLog {

    private Long id;

    private String cardId;

    private String thirdUserId;

    private String operateType;

    private String operator;

    private String reason;

    private LocalDateTime operateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }

    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }

    public String getOperateType() { return operateType; }
    public void setOperateType(String operateType) { this.operateType = operateType; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getOperateTime() { return operateTime; }
    public void setOperateTime(LocalDateTime operateTime) { this.operateTime = operateTime; }
}
