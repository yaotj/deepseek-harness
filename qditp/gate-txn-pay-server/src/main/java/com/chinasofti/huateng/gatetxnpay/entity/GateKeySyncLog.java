package com.chinasofti.huateng.gatetxnpay.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AGM 密钥同步日志实体。
 */
public class GateKeySyncLog implements Serializable {
    private static final long serialVersionUID = 1L;

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

    @Override
    public String toString() {
        return "GateKeySyncLog{" +
                "id=" + id +
                ", deviceId='" + deviceId + '\'' +
                ", syncDate=" + syncDate +
                ", reqKeyCount=" + reqKeyCount +
                ", respRetCode='" + respRetCode + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
