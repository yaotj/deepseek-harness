package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.device.DeviceInfo;
import com.chinasofti.huateng.para.model.device.DeviceSyncResult;
import com.github.pagehelper.PageInfo;

import java.util.List;

/** 设备信息参数（台账 + 业主自定义管理模式）运营端服务。 */
public interface DeviceInfoService {
    /** 设备管理页分页查询，条件全部可选。 */
    ResultVO<PageInfo<DeviceInfo>> pageDevices(String lineId, String stationId, String deviceType,
                                               String manageMode, String keyword, boolean includeDeleted,
                                               Integer pageNum, Integer pageSize);

    /** 批量设置业主自定义管理模式（01 正常使用 / 02 节能关机）。 */
    ResultVO<Integer> updateManageMode(List<String> deviceIds, String manageMode);

    /** 手动触发一次 ACC 设备台账全量同步。 */
    ResultVO<DeviceSyncResult> syncFromAcc();
}
