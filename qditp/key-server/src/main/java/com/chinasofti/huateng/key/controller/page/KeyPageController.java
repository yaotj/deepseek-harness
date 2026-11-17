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

    @GetMapping("/versions")
    public ResultVO<List<KeyVersionView>> versions() {
        return keyVersionQueryService.versions();
    }


    @PostMapping("/agm-version/approval")
    public ResultVO<Void> approveAgmVersion(@RequestBody AgmApprovalRequest request) {
        return agmKeyApprovalService.approve(
                request == null ? null : request.providerId(),
                request == null ? null : request.batchNumber(),
                request == null ? null : request.targetStatus(),
                request == null ? null : request.operator());
    }

    public record AgmApprovalRequest(String providerId, Long batchNumber, String targetStatus, String operator) {
    }


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
