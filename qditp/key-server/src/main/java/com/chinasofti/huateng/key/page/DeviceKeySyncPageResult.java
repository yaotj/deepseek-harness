package com.chinasofti.huateng.key.page;

import java.util.List;


public class DeviceKeySyncPageResult {

    private List<DeviceKeySyncView> list;

    private long total;

    public List<DeviceKeySyncView> getList() {
        return list;
    }

    public void setList(List<DeviceKeySyncView> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
