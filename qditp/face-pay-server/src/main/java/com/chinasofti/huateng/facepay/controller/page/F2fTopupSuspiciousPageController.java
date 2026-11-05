package com.chinasofti.huateng.facepay.controller.page;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.facepay.api.page.F2fTopupSuspiciousPageVO;
import com.chinasofti.huateng.facepay.service.F2fTopupSuspiciousQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/page/face-pay/topup-suspicious")
public class F2fTopupSuspiciousPageController {

    private final F2fTopupSuspiciousQueryService topupSuspiciousQueryService;

    public F2fTopupSuspiciousPageController(F2fTopupSuspiciousQueryService topupSuspiciousQueryService) {
        this.topupSuspiciousQueryService = topupSuspiciousQueryService;
    }

    @GetMapping
    public ResultVO<Map<String, Object>> page(@RequestParam(required = false) String orderNo,
                                              @RequestParam(required = false) String channel,
                                              @RequestParam(required = false) String deviceId,
                                              @RequestParam(required = false) String topupStatus,
                                              @RequestParam(required = false) String processed,
                                              @RequestParam(required = false) String beginTime,
                                              @RequestParam(required = false) String endTime,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        F2fTopupSuspiciousQueryService.TopupSuspiciousPageQuery query =
                new F2fTopupSuspiciousQueryService.TopupSuspiciousPageQuery(
                        orderNo, channel, deviceId, topupStatus, processed, beginTime, endTime,
                        pageNum, pageSize);
        return switch (topupSuspiciousQueryService.page(query)) {
            case F2fTopupSuspiciousQueryService.PageOutcome.Ok ok -> {
                Map<String, Object> page = new LinkedHashMap<>();
                page.put("list", ok.list());
                page.put("total", ok.total());
                yield ResultMapper.ok(page);
            }
            case F2fTopupSuspiciousQueryService.PageOutcome.Rejected rejected ->
                    ResultMapper.illegalParams(rejected.reason());
        };
    }
}
