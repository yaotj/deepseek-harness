package com.chinasofti.huateng.facepay.controller.page;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.facepay.api.page.AppPartialRefundRequest;
import com.chinasofti.huateng.facepay.service.F2fPageRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/page/app/orders")
public class AppOrderPageController {

    private static final Logger log = LoggerFactory.getLogger(AppOrderPageController.class);

    private final F2fPageRefundService pageRefundService;

    public AppOrderPageController(F2fPageRefundService pageRefundService) {
        this.pageRefundService = pageRefundService;
    }

    @PostMapping("/{orderNo}/refund")
    public ResultVO<?> refundByAmount(@PathVariable String orderNo,
                                      @RequestBody(required = false) AppPartialRefundRequest request) {
        Long refundAmount = request == null ? null : request.getRefundAmount();
        String reason = request == null ? null : request.getRefundReason();
        String operatorId = request == null ? null : request.getOperatorId();
        log.info("接收到 APP 订单按金额退款请求, orderNo={}, refundAmount={}, operatorId={}",
                orderNo, refundAmount, operatorId);
        if (orderNo == null || orderNo.isBlank()) {
            return ResultMapper.error("订单号不能为空");
        }
        return pageRefundService.refundByAmount(orderNo, refundAmount, reason, operatorId);
    }
}
