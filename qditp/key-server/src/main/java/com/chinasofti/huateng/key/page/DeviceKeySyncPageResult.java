package com.chinasofti.huateng.key.page;

import java.util.List;

/**
 * 设备密钥同步分页结果。
 */
public class DeviceKeySyncPageResult {

    /** 当前页数据。 */
    private List<DeviceKeySyncView> list;

    /** 总记录数。 */
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
