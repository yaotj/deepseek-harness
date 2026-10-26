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

/**
 * 运营端设备信息参数接口（综管台「设备管理」页）。
 * <p>台账数据来自 ACC 同步，页面可维护业主自定义管理模式；手动同步供「立即同步」按钮使用。</p>
 */
@RestController
@RequestMapping("/page/device")
public class DeviceInfoController {

    private final DeviceInfoService deviceInfoService;

    public DeviceInfoController(DeviceInfoService deviceInfoService) {
        this.deviceInfoService = deviceInfoService;
    }

    /** 分页查询设备台账，条件全部可选；keyword 同时模糊匹配设备编号与设备名称。 */
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

    /** 批量设置业主自定义管理模式，请求体 deviceIds + manageMode（01 正常使用 / 02 节能关机）。 */
    @PutMapping("/manage-mode")
    public ResultVO<Integer> updateManageMode(@RequestBody ManageModeRequest request) {
        return deviceInfoService.updateManageMode(request.getDeviceIds(), request.getManageMode());
    }

    /** 手动触发一次 ACC 设备台账全量同步，同步可能耗时数十秒，前端按钮需延长超时。 */
    @PostMapping("/sync")
    public ResultVO<DeviceSyncResult> sync() {
        return deviceInfoService.syncFromAcc();
    }

    /** 批量设置管理模式请求体。 */
    public static class ManageModeRequest {
        private List<String> deviceIds;
        private String manageMode;

        public List<String> getDeviceIds() { return deviceIds; }
        public void setDeviceIds(List<String> deviceIds) { this.deviceIds = deviceIds; }
        public String getManageMode() { return manageMode; }
        public void setManageMode(String manageMode) { this.manageMode = manageMode; }
    }
}
