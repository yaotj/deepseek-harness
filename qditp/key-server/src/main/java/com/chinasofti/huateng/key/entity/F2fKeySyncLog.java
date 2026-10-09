package com.chinasofti.huateng.key.entity;

import java.time.LocalDateTime;

/**
 * AGM 密钥同步日志实体（每个设备一行，当面付业务）。
 */
public class F2fKeySyncLog {
    private Long id;
    private String deviceId;
    private LocalDateTime syncDate;
    private String reqBizData;
    private Integer reqKeyCount;
    private String respRetCode;
    private String respRetMsg;
    private Integer respKeyVersionCount;
    private Long processDurationMs;
    private String status;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public LocalDateTime getSyncDate() { return syncDate; }
    public void setSyncDate(LocalDateTime syncDate) { this.syncDate = syncDate; }

    public String getReqBizData() { return reqBizData; }
    public void setReqBizData(String reqBizData) { this.reqBizData = reqBizData; }

    public Integer getReqKeyCount() { return reqKeyCount; }
    public void setReqKeyCount(Integer reqKeyCount) { this.reqKeyCount = reqKeyCount; }

    public String getRespRetCode() { return respRetCode; }
    public void setRespRetCode(String respRetCode) { this.respRetCode = respRetCode; }

    public String getRespRetMsg() { return respRetMsg; }
    public void setRespRetMsg(String respRetMsg) { this.respRetMsg = respRetMsg; }

    public Integer getRespKeyVersionCount() { return respKeyVersionCount; }
    public void setRespKeyVersionCount(Integer respKeyVersionCount) { this.respKeyVersionCount = respKeyVersionCount; }

    public Long getProcessDurationMs() { return processDurationMs; }
    public void setProcessDurationMs(Long processDurationMs) { this.processDurationMs = processDurationMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "F2fKeySyncLog{" +
                "deviceId='" + deviceId + '\'' +
                ", syncDate=" + syncDate +
                ", status='" + status + '\'' +
                ", respRetCode='" + respRetCode + '\'' +
                '}';
    }
}
