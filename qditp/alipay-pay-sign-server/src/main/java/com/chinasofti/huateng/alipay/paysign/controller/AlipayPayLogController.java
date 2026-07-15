package com.chinasofti.huateng.alipay.paysign.controller;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPaySignService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付宝出行订单 Controller。
 */
@RestController
@RequestMapping("/api/payment")
public class AlipayPayLogController {

    private final AlipayPaySignService alipayPaySignService;

    public AlipayPayLogController(AlipayPaySignService alipayPaySignService) {
        this.alipayPaySignService = alipayPaySignService;
    }

    /**
     * 查询支付宝出行订单列表。
     */
    @GetMapping("/payLog/list")
    public Map<String, Object> list(@RequestParam(required = false) String thirdUserId,
                                    @RequestParam(required = false) String cardId,
                                    @RequestParam(required = false) String payStatus,
                                    @RequestParam(required = false) String startTime,
                                    @RequestParam(required = false) String endTime,
                                    @RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "10") int pageSize) {
        Map<String, Object> result = new HashMap<>();
        int offset = (pageNum - 1) * pageSize;
        List<AlipayPayLog> list = alipayPaySignService.selectAlipayPayLogList(thirdUserId, cardId, payStatus, startTime, endTime, offset, pageSize);
        int total = alipayPaySignService.countAlipayPayLogList(thirdUserId, cardId, payStatus, startTime, endTime);
        result.put("list", list);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }
}
