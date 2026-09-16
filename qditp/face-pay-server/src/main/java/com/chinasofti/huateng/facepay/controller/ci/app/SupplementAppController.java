package com.chinasofti.huateng.facepay.controller.ci.app;

import com.chinasofti.huateng.facepay.service.supplement.SupplementOrderService;
import com.chinasofti.huateng.model.pay.SupplementOrderReqDTO;
import com.chinasofti.huateng.model.pay.SupplementOrderRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ci/facePay/app")
public class SupplementAppController {

    private static final Logger log = LoggerFactory.getLogger(SupplementAppController.class);

    private final SupplementOrderService supplementOrderService;

    public SupplementAppController(SupplementOrderService supplementOrderService) {
        this.supplementOrderService = supplementOrderService;
    }

    @PostMapping("/requestPayOrder")
    public SupplementOrderRespDTO requestPayOrder(@RequestBody SupplementOrderReqDTO request) {
        log.info("补款下单请求, request={}", request);
        SupplementOrderRespDTO result = supplementOrderService.requestPayOrder(request);
        log.info("补款下单结果, orderNo={}, payStatus={}",
                result != null ? result.getOrderNo() : null,
                result != null ? result.getPayStatus() : null);
        return result;
    }
}
