package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.device.DeviceInfo;
import com.chinasofti.huateng.para.model.device.DeviceSyncResult;
import com.github.pagehelper.PageInfo;

import java.util.List;

public interface DeviceInfoService {
    ResultVO<PageInfo<DeviceInfo>> pageDevices(String lineId, String stationId, String deviceType,
                                               String manageMode, String keyword, boolean includeDeleted,
                                               Integer pageNum, Integer pageSize);

    ResultVO<Integer> updateManageMode(List<String> deviceIds, String manageMode);

    ResultVO<DeviceSyncResult> syncFromAcc();
}
