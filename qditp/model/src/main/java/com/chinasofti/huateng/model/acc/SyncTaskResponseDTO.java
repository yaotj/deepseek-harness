package com.chinasofti.huateng.model.acc;

import java.util.ArrayList;
import java.util.List;

/**
 * desc:2001-任务获取请求-返回报文
 **/
public class SyncTaskResponseDTO {

    /**
     * desc:任务目标节点列表
     **/
    private List<SyncTaskResponse1> toDevNodeIdList = new ArrayList<>();

    public List<SyncTaskResponse1> getToDevNodeIdList() {
        return toDevNodeIdList;
    }

    public void setToDevNodeIdList(List<SyncTaskResponse1> toDevNodeIdList) {
        this.toDevNodeIdList = toDevNodeIdList;
    }
}
