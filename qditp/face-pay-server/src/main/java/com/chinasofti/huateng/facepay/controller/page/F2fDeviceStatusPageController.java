package com.chinasofti.huateng.facepay.controller.page;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.facepay.service.F2fDeviceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/page/face-pay/devices")
public class F2fDeviceStatusPageController {

    private final F2fDeviceQueryService deviceQueryService;

    public F2fDeviceStatusPageController(F2fDeviceQueryService deviceQueryService) {
        this.deviceQueryService = deviceQueryService;
    }

    @GetMapping
    public ResultVO<Map<String, Object>> page(@RequestParam(required = false) String channel,
                                              @RequestParam(required = false) String onlineFlag,
                                              @RequestParam(required = false) String deviceId,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        F2fDeviceQueryService.DevicePageQuery query = new F2fDeviceQueryService.DevicePageQuery(
                channel, onlineFlag, deviceId, pageNum, pageSize);
        return switch (deviceQueryService.page(query)) {
            case F2fDeviceQueryService.PageOutcome.Ok ok -> {
                Map<String, Object> page = new LinkedHashMap<>();
                page.put("list", ok.list());
                page.put("total", ok.total());
                page.put("summary", ok.summary());
                yield ResultMapper.ok(page);
            }
            case F2fDeviceQueryService.PageOutcome.Rejected rejected ->
                    ResultMapper.illegalParams(rejected.reason());
        };
    }
}
