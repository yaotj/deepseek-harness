package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.device.DeviceInfo;
import com.chinasofti.huateng.para.model.device.DeviceSyncResult;
import com.chinasofti.huateng.para.service.DeviceInfoService;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/page/device")
public class DeviceInfoController {

    private final DeviceInfoService deviceInfoService;

    public DeviceInfoController(DeviceInfoService deviceInfoService) {
        this.deviceInfoService = deviceInfoService;
    }

    @GetMapping("/list")
    public ResultVO<PageInfo<DeviceInfo>> list(@RequestParam(required = false) String lineId,
                                               @RequestParam(required = false) String stationId,
                                               @RequestParam(required = false) String deviceType,
                                               @RequestParam(required = false) String manageMode,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "false") boolean includeDeleted,
                                               @RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize) {
        return deviceInfoService.pageDevices(lineId, stationId, deviceType, manageMode, keyword,
                includeDeleted, pageNum, pageSize);
    }

    @PutMapping("/manage-mode")
    public ResultVO<Integer> updateManageMode(@RequestBody ManageModeRequest request) {
        return deviceInfoService.updateManageMode(request.getDeviceIds(), request.getManageMode());
    }

    @PostMapping("/sync")
    public ResultVO<DeviceSyncResult> sync() {
        return deviceInfoService.syncFromAcc();
    }

    public static class ManageModeRequest {
        private List<String> deviceIds;
        private String manageMode;

        public List<String> getDeviceIds() { return deviceIds; }
        public void setDeviceIds(List<String> deviceIds) { this.deviceIds = deviceIds; }
        public String getManageMode() { return manageMode; }
        public void setManageMode(String manageMode) { this.manageMode = manageMode; }
    }
}
