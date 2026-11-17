package com.chinasofti.huateng.account.controller.ci.app;

import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.account.service.AccountCancelService;
import com.chinasofti.huateng.account.service.AccountProfileService;
import com.chinasofti.huateng.account.service.PhoneChangeService;
import com.chinasofti.huateng.account.service.AccountRegistrationService;
import com.chinasofti.huateng.account.service.PayChannelService;
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
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractReqDTO;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractResult;
import com.chinasofti.huateng.model.app.UserCancelReqDTO;
import com.chinasofti.huateng.model.app.UserCancelResult;
import com.chinasofti.huateng.common.response.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RequestApplicationController {
    private static final Logger log = LoggerFactory.getLogger(RequestApplicationController.class);


    private final AccountCancelService accountCancelService;


    private final AccountProfileService accountProfileService;


    private final PhoneChangeService phoneChangeService;


    private final PayChannelService payChannelService;


    private final AccountRegistrationService accountRegistrationService;


    private final AccountRequestVerifier accountRequestVerifier;


    public RequestApplicationController(AccountCancelService accountCancelService,
                                        AccountProfileService accountProfileService,
                                        PhoneChangeService phoneChangeService,
                                        PayChannelService payChannelService,
                                        AccountRegistrationService accountRegistrationService,
                                        AccountRequestVerifier accountRequestVerifier) {
        this.accountCancelService = accountCancelService;
        this.accountProfileService = accountProfileService;
        this.phoneChangeService = phoneChangeService;
        this.payChannelService = payChannelService;
        this.accountRegistrationService = accountRegistrationService;
        this.accountRequestVerifier = accountRequestVerifier;
    }

    @PostMapping("/requestApplication")
    public RequestApplicationResult requestApplication(@RequestBody RequestApplicationReqDTO request) {
        log.info("接收到请求开户接口报文: {}", request);
        return accountRegistrationService.requestApplication(request);
    }

    @PostMapping("/requestAddPayChannel")
    public RequestAddPayChannelResult requestAddPayChannel(@RequestBody RequestAddPayChannelReqDTO request) {
        log.info("接收到请求添加支付通道接口报文: {}", request);
        return payChannelService.requestAddPayChannel(request);
    }

    @PostMapping("/requestSetDefaultPayChannel")
    public RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(@RequestBody RequestSetDefaultPayChannelReqDTO request) {
        log.info("接收到请求设置默认支付通道接口报文: {}", request);
        return payChannelService.requestSetDefaultPayChannel(request);
    }

    @PostMapping("/requestRemovePayChannel")
    public RequestRemovePayChannelResult requestRemovePayChannel(@RequestBody RequestRemovePayChannelReqDTO request) {
        log.info("接收到删除支付通道接口报文: {}", request);
        return payChannelService.requestRemovePayChannel(request);
    }


    @PostMapping("/requestAgreeRelease")
    public RequestRemovePayChannelResult requestAgreeRelease(@RequestBody RequestRemovePayChannelReqDTO request) {
        log.info("接收到钱包解绑接口报文: {}", request);
        return payChannelService.requestAgreeRelease(request);
    }


    @PostMapping("/userCancel")
    public UserCancelResult userCancel(@RequestBody UserCancelReqDTO request) {
        log.info("接收到IF8A-42用户销户接口报文: {}", request);
        return accountCancelService.userCancel(request);
    }

    @PostMapping("/queryUserInfo")
    public QueryUserInfoResult queryUserInfo(@RequestBody QueryUserInfoReqDTO request) {
        log.info("接收到查询用户信息接口报文: {}", request);
        return accountProfileService.queryUserInfo(request);
    }

    @PostMapping("/requestUpdateChannelDefaultContract")
    public RequestUpdateChannelDefaultContractResult requestUpdateChannelDefaultContract(@RequestBody RequestUpdateChannelDefaultContractReqDTO request) {
        log.info("接收到更换第三方渠道码默认支付方式接口报文: {}", request);
        return payChannelService.requestUpdateChannelDefaultContract(request);
    }

    @GetMapping("/updatePhone")
    public CommonResult updatePhone(@RequestParam String thirdUserId, @RequestParam String newMsisdn) {
        log.info("接收到更换手机号请求: thirdUserId={}, newMsisdn={}", thirdUserId, newMsisdn);
        boolean result = phoneChangeService.updatePhone(thirdUserId, newMsisdn);
        CommonResult response = new CommonResult();
        if (result) {
            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("更换手机号成功");
        } else {
            response.setRetCode(AccountErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("更换手机号失败");
        }
        return response;
    }
}
