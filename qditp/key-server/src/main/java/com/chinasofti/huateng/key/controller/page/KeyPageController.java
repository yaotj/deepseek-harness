package com.chinasofti.huateng.key.controller.page;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.key.page.DeviceKeySyncPageResult;
import com.chinasofti.huateng.key.page.KeyVersionView;
import com.chinasofti.huateng.key.service.DeviceKeySyncQueryService;
import com.chinasofti.huateng.key.service.KeyVersionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 综管台密钥版本查看入口（只读）。响应 NEVER 包含任何密钥材料明文。 */
@RestController
@RequestMapping("/page/key")
public class KeyPageController {

    private final KeyVersionQueryService keyVersionQueryService;
    private final DeviceKeySyncQueryService deviceKeySyncQueryService;

    public KeyPageController(KeyVersionQueryService keyVersionQueryService,
                             DeviceKeySyncQueryService deviceKeySyncQueryService) {
        this.keyVersionQueryService = keyVersionQueryService;
        this.deviceKeySyncQueryService = deviceKeySyncQueryService;
    }

    /** 各密钥域（AGM / CA 密钥仓库 / HCE 静态密钥）当前版本汇总。 */
    @GetMapping("/versions")
    public ResultVO<List<KeyVersionView>> versions() {
        return keyVersionQueryService.versions();
    }

    /**
     * 设备密钥同步状态分页查询（源表 COM_DEVICE_SYN_KEY，每台设备仅最新一条）。
     */
    @GetMapping("/device-sync/list")
    public ResultVO<DeviceKeySyncPageResult> deviceKeySyncList(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String stationCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return deviceKeySyncQueryService.query(deviceId, stationCode, status, pageNum, pageSize);
    }
}
