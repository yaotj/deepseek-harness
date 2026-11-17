package com.chinasofti.huateng.model.acc;

import java.util.ArrayList;
import java.util.List;


public class ReportTaskDTO {
    List<ReportTask1> taskList = new ArrayList<>();

    public List<ReportTask1> getTaskList() {
        return taskList;
    }

    public void setTaskList(List<ReportTask1> taskList) {
        this.taskList = taskList;
    }
}
