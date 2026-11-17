package com.chinasofti.huateng.model.para;

import java.io.Serializable;

public class SyncTaskDTO implements Serializable {

    private static final long serialVersionUID = 1L;


    private String taskId;


    private String srcNodeCode;


    private String destNodeCode;


    private String waterNo;


    private String taskCode;


    private String taskTime;


    private String taskContent;


    private String insertDateTime;


    private String syncDateTime;


    private String taskStatus;


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
