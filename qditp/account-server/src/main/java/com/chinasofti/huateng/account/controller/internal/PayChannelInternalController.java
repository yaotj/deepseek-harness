package com.chinasofti.huateng.account.controller.internal;

import com.chinasofti.huateng.account.service.PayChannelInternalService;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractReqDTO;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractResult;
import com.chinasofti.huateng.model.app.SyncPayAccountIdReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class PayChannelInternalController {
    private static final Logger log = LoggerFactory.getLogger(PayChannelInternalController.class);

    private final PayChannelInternalService payChannelInternalService;


    public PayChannelInternalController(PayChannelInternalService payChannelInternalService) {
        this.payChannelInternalService = payChannelInternalService;
    }


    @PostMapping("/queryPayChannelByContractNo")
    public QueryPayChannelByContractResult queryPayChannelByContractNo(
            @RequestBody QueryPayChannelByContractReqDTO request) {
        log.info("接收到按签约流水号查询支付通道报文: {}", request);
        return payChannelInternalService.queryPayChannelByContractNo(request);
    }


    @PostMapping("/internal/payChannel/syncPayAccountId")
    public CommonResult syncPayAccountId(@RequestBody SyncPayAccountIdReqDTO request) {
        log.info("接收到支付域回写支付账号报文: {}", request);
        return payChannelInternalService.syncPayAccountId(request);
    }
}
