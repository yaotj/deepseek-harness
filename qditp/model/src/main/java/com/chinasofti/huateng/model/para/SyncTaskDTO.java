package com.chinasofti.huateng.model.para;

import java.io.Serializable;

public class SyncTaskDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务源节点(8位)
     */
    private String srcNodeCode;

    /**
     * 任务目标节点(8位)
     */
    private String destNodeCode;

    /**
     * 任务流水号（预留)
     */
    private String waterNo;

    /**
     * 任务编码(报文编号4位)
     */
    private String taskCode;

    /**
     * 任务时间
     */
    private String taskTime;

    /**
     * 任务内容(4096)
     */
    private String taskContent;

    /**
     * 任务入库时间(分区字段)
     */
    private String insertDateTime;

    /**
     * 任务下发时间(设备查询取走时间)
     */
    private String syncDateTime;

    /**
     * 任务状态  00: 初始化 01：下级节点请求获取  02：下级节点获取确认 03：下级节点执行成功 04: 下级节点执行失败
     */
    private String taskStatus;

    /**
     * 状态上报时间
     */
    private String reportDateTime;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSrcNodeCode() {
        return srcNodeCode;
    }

    public void setSrcNodeCode(String srcNodeCode) {
        this.srcNodeCode = srcNodeCode;
    }

    public String getDestNodeCode() {
        return destNodeCode;
    }

    public void setDestNodeCode(String destNodeCode) {
        this.destNodeCode = destNodeCode;
    }

    public String getWaterNo() {
        return waterNo;
    }

    public void setWaterNo(String waterNo) {
        this.waterNo = waterNo;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getTaskTime() {
        return taskTime;
    }

    public void setTaskTime(String taskTime) {
        this.taskTime = taskTime;
    }

    public String getTaskContent() {
        return taskContent;
    }

    public void setTaskContent(String taskContent) {
        this.taskContent = taskContent;
    }

    public String getInsertDateTime() {
        return insertDateTime;
    }

    public void setInsertDateTime(String insertDateTime) {
        this.insertDateTime = insertDateTime;
    }

    public String getSyncDateTime() {
        return syncDateTime;
    }

    public void setSyncDateTime(String syncDateTime) {
        this.syncDateTime = syncDateTime;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public String getReportDateTime() {
        return reportDateTime;
    }

    public void setReportDateTime(String reportDateTime) {
        this.reportDateTime = reportDateTime;
    }
}
