package com.chinasofti.huateng.account.entity;

import java.time.LocalDateTime;

/**
 * ACC 员工码操作日志表实体。
 */
public class UserAccEmployeeCardLog {
    private Long id;
    private String cardNo;
    private String eventType;
    private Integer cardStatus;
    private String remark;
    private LocalDateTime createTms;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Integer getCardStatus() { return cardStatus; }
    public void setCardStatus(Integer cardStatus) { this.cardStatus = cardStatus; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTms() { return createTms; }
    public void setCreateTms(LocalDateTime createTms) { this.createTms = createTms; }
}
