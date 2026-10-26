package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.para.model.device.DeviceSyncResult;
import com.chinasofti.huateng.para.service.AccDeviceSyncService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供 web-server Quartz 定时任务调用的设备台账同步端点（仅集群内可达，无鉴权）。
 * <p>口径与 {@code ParaImportController} 的 FTP 扫描 Quartz 端点一致：
 * retCode 0000 全部成功、9999 同步失败，retMsg 带执行摘要。</p>
 */
@RestController
@RequestMapping("/para/device-sync")
public class DeviceSyncInternalController {

    private final AccDeviceSyncService accDeviceSyncService;

    public DeviceSyncInternalController(AccDeviceSyncService accDeviceSyncService) {
        this.accDeviceSyncService = accDeviceSyncService;
    }

    /** 全量同步 ACC 设备台账，同步幂等可重跑。 */
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
