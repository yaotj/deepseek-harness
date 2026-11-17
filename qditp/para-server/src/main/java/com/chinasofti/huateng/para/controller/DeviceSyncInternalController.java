package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.para.model.device.DeviceSyncResult;
import com.chinasofti.huateng.para.service.AccDeviceSyncService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/para/device-sync")
public class DeviceSyncInternalController {

    private final AccDeviceSyncService accDeviceSyncService;

    public DeviceSyncInternalController(AccDeviceSyncService accDeviceSyncService) {
        this.accDeviceSyncService = accDeviceSyncService;
    }

    @PostMapping("/quartz")
    public CommonResult syncForQuartz() {
        CommonResult result = new CommonResult();
        try {
            DeviceSyncResult syncResult = accDeviceSyncService.syncAll();
            result.setRetCode("0000");
            result.setRetMsg(String.format(
                    "ACC设备台账同步完成: 级别=%s, 页数=%d, 拉取=%d, 落库=%d, 耗时=%dms",
                    syncResult.getManageLevels(), syncResult.getPages(), syncResult.getFetched(),
                    syncResult.getMerged(), syncResult.getElapsedMillis()));
        } catch (Exception exception) {
            result.setRetCode("9999");
            result.setRetMsg("ACC设备台账同步失败: " + exception.getMessage());
        }
        return result;
    }
}
