package com.chinasofti.huateng.model.acc;

import java.util.ArrayList;
import java.util.List;


public class SearchSyncTaskDTO {

    List<String> devNodeIdArray = new ArrayList<>();

    public List<String> getDevNodeIdArray() {
        return devNodeIdArray;
    }

    public void setDevNodeIdArray(List<String> devNodeIdArray) {
        this.devNodeIdArray = devNodeIdArray;
    }
}
