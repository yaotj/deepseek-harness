package com.chinasofti.huateng.key.controller.page;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.key.page.DeviceKeySyncPageResult;
import com.chinasofti.huateng.key.page.KeyVersionView;
import com.chinasofti.huateng.key.service.AgmKeyApprovalService;
import com.chinasofti.huateng.key.service.DeviceKeySyncQueryService;
import com.chinasofti.huateng.key.service.KeyVersionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 综管台密钥版本入口。查询端只读、响应 NEVER 含密钥材料明文；审批端只改审批状态元信息
 * （安全红线动作，MUST 经人工安全合规复核后上线，见 docs/tracking 对应重档）。
 */
@RestController
@RequestMapping("/page/key")
public class KeyPageController {

    private final KeyVersionQueryService keyVersionQueryService;
    private final DeviceKeySyncQueryService deviceKeySyncQueryService;
    private final AgmKeyApprovalService agmKeyApprovalService;

    public KeyPageController(KeyVersionQueryService keyVersionQueryService,
                             DeviceKeySyncQueryService deviceKeySyncQueryService,
                             AgmKeyApprovalService agmKeyApprovalService) {
        this.keyVersionQueryService = keyVersionQueryService;
        this.deviceKeySyncQueryService = deviceKeySyncQueryService;
        this.agmKeyApprovalService = agmKeyApprovalService;
    }

    /** 各密钥域（AGM / CA 密钥仓库 / HCE 静态密钥）当前版本汇总。 */
    @GetMapping("/versions")
    public ResultVO<List<KeyVersionView>> versions() {
        return keyVersionQueryService.versions();
    }

    /**
     * AGM 密钥版本审批（操作型）：只允许 20010 初始化 → 20020 审批通过 / 20030 拒绝。
     *
     * <p>请求体：{@code {providerId, batchNumber, targetStatus, operator}}。
     * 仅改审批状态元信息，不生成 / 不返回密钥材料。安全红线，上线前 MUST 人工安全复核。</p>
     */
    @PostMapping("/agm-version/approval")
    public ResultVO<Void> approveAgmVersion(@RequestBody AgmApprovalRequest request) {
        return agmKeyApprovalService.approve(
                request == null ? null : request.providerId(),
                request == null ? null : request.batchNumber(),
                request == null ? null : request.targetStatus(),
                request == null ? null : request.operator());
    }

    /** AGM 密钥版本审批请求体。 */
    public record AgmApprovalRequest(String providerId, Long batchNumber, String targetStatus, String operator) {
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
