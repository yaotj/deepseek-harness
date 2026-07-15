package com.chinasofti.huateng.account.controller.ci.app;

import com.chinasofti.huateng.account.service.AccountApplicationService;
import com.chinasofti.huateng.account.service.AccountRequestVerifier;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RequestApplicationController {
    private static final Logger log = LoggerFactory.getLogger(RequestApplicationController.class);

    @Autowired
    private AccountApplicationService accountApplicationService;

    @Autowired
    private AccountRequestVerifier accountRequestVerifier;

    @PostMapping("/requestApplication")
    public RequestApplicationResult requestApplication(@RequestBody RequestApplicationReqDTO request) {
        log.info("接收到请求开户接口报文: {}", request);
        return accountApplicationService.requestApplication(request);
    }

    @PostMapping("/requestAddPayChannel")
    public RequestAddPayChannelResult requestAddPayChannel(@RequestBody RequestAddPayChannelReqDTO request) {
        log.info("接收到请求添加支付通道接口报文: {}", request);
        return accountApplicationService.requestAddPayChannel(request);
    }

    @PostMapping("/requestSetDefaultPayChannel")
    public RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(@RequestBody RequestSetDefaultPayChannelReqDTO request) {
        log.info("接收到请求设置默认支付通道接口报文: {}", request);
        return accountApplicationService.requestSetDefaultPayChannel(request);
    }

    @PostMapping("/requestRemovePayChannel")
    public RequestRemovePayChannelResult requestRemovePayChannel(@RequestBody RequestRemovePayChannelReqDTO request) {
        log.info("接收到删除支付通道接口报文: {}", request);
        return accountApplicationService.requestRemovePayChannel(request);
    }

    @PostMapping("/queryUserInfo")
    public QueryUserInfoResult queryUserInfo(@RequestBody QueryUserInfoReqDTO request) {
        log.info("接收到查询用户信息接口报文: {}", request);
        return accountApplicationService.queryUserInfo(request);
    }
}
