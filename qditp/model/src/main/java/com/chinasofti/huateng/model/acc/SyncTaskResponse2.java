package com.chinasofti.huateng.model.acc;

public class SyncTaskResponse2 {
    /**
     * desc:任务ID
     **/
    private String taskId;

    /**
     * desc:任务源节点
     **/
    private String srcDevNodeId;

    /**
     * desc:任务时间,YYYYMMDDhhmmss
     **/
    private String taskTime;

    /**
     * desc:任务编码
     **/
    private String taskCode;

    /**
     * desc:任务数据
     **/
    private String taskData;

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

    public String getTaskTime() {
        return taskTime;
    }

    public void setTaskTime(String taskTime) {
        this.taskTime = taskTime;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getTaskData() {
        return taskData;
    }

    public void setTaskData(String taskData) {
        this.taskData = taskData;
    }
}
