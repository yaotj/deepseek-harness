package com.chinasofti.huateng.collectpay.controller.page;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.service.AppOrderService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 运营端 APP 取票订单退款（**按指定金额**）。 */
@Slf4j
@RestController
@RequestMapping("/page/app/orders")
public class AppOrderPageController {

    private final AppOrderService appOrderService;

    public AppOrderPageController(AppOrderService appOrderService) {
        this.appOrderService = appOrderService;
    }

    /** 按指定金额给 APP 取票订单退款，用于补退剩余部分。 */
    @PostMapping("/{orderNo}/refund")
    public ResultVO<JSONObject> refundByAmount(@PathVariable String orderNo,
                                              @RequestBody(required = false) AppPartialRefundRequest request) {
        log.info("接收到运营端 APP 指定金额退款请求, orderNo={}, request={}", orderNo,
                request == null ? null : request.getRefundAmount());
        if (!StringUtils.hasText(orderNo)) {
            return ResultMapper.illegalParams("订单号不能为空");
        }
        if (request == null || request.getRefundAmount() == null) {
            return ResultMapper.illegalParams("退款金额不能为空");
        }
        if (request.getRefundAmount() <= 0) {
            return ResultMapper.illegalParams("退款金额必须为正整数（单位：分）");
        }

        JSONObject result = appOrderService.refundByAmount(orderNo.trim(), request.getRefundAmount());
        String retCode = result == null ? null : result.getString("retCode");
        return "0000".equals(retCode) ? ResultMapper.ok(result)
                : ResultMapper.error(result == null ? "退款服务未返回结果" : result.getString("retMsg"));
    }
}
