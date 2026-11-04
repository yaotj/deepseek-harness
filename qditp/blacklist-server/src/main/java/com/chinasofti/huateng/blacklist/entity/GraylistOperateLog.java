package com.chinasofti.huateng.blacklist.entity;

import java.time.LocalDateTime;

/** 灰名单操作记录实体（进灰 ADD / 移出 REMOVE 流水）。 */
public class GraylistOperateLog {

    /** 主键ID。 */
    private Long id;

    /** 卡ID。 */
    private String cardId;

    /** 三方用户ID。 */
    private String thirdUserId;

    /** 操作类型：ADD 进灰，REMOVE 移出。 */
    private String operateType;

    /** 操作者。 */
    private String operator;

    /** 本次操作原因。 */
    private String reason;

    /** 操作时间。 */
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
