package com.chinasofti.huateng.model.acc;

import java.util.ArrayList;
import java.util.List;


public class SyncTaskResponseDTO {


    private List<SyncTaskResponse1> toDevNodeIdList = new ArrayList<>();

    public List<SyncTaskResponse1> getToDevNodeIdList() {
        return toDevNodeIdList;
    }

    public void setToDevNodeIdList(List<SyncTaskResponse1> toDevNodeIdList) {
        this.toDevNodeIdList = toDevNodeIdList;
    }
}
