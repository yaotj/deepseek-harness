package com.chinasofti.huateng.model.acc;

public class ReportTask1 {


    String taskId;


    String srcDevNodeId;


    String exeDevNodeId;


    String taskReportTime;


    String taskStatus;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSrcDevNodeId() {
        return srcDevNodeId;
    }

    public void setSrcDevNodeId(String srcDevNodeId) {
        this.srcDevNodeId = srcDevNodeId;
    }

    public String getTaskReportTime() {
        return taskReportTime;
    }

    public void setTaskReportTime(String taskReportTime) {
        this.taskReportTime = taskReportTime;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public String getExeDevNodeId() {
        return exeDevNodeId;
    }

    public void setExeDevNodeId(String exeDevNodeId) {
        this.exeDevNodeId = exeDevNodeId;
    }
}
