package com.chinasofti.huateng.para.entity.risk;

import java.time.LocalDateTime;

public class RiskGroup {
    private Long groupId;

    private String groupName;

    private String groupDesc;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getGroupDesc() { return groupDesc; }
    public void setGroupDesc(String groupDesc) { this.groupDesc = groupDesc; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
