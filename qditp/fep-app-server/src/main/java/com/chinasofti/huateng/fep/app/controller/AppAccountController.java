package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.model.app.ItpCommonFormRequest;
import com.chinasofti.huateng.fep.app.service.AccountAppService;
import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;
import com.chinasofti.huateng.model.app.RequestKeyListReqDTO;
import com.chinasofti.huateng.model.app.RequestKeyListResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractReqDTO;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractResult;
import com.chinasofti.huateng.model.app.UserCancelReqDTO;
import com.chinasofti.huateng.model.app.UserCancelResult;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryResult;
import com.chinasofti.huateng.model.employee.EmployeeCardActivateReqDTO;
import com.chinasofti.huateng.common.response.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 账户及支付相关接口入口。
 */
@RestController
public class AppAccountController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppAccountController.class);

    private final AccountAppService accountAppService;

    public AppAccountController(AccountAppService accountAppService) {
        this.accountAppService = accountAppService;
    }

    @PostMapping({"/ci/app/requestApplication", "/app/requestApplication"})
    public RequestApplicationResult requestApplication(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-01 请求开户, 请求参数: {}", request);
        return accountAppService.requestApplication(parseBizData(request, RequestApplicationReqDTO.class));
    }

    @PostMapping({"/ci/app/requestKeyList", "/app/requestKeyList"})
    public RequestKeyListResult requestKeyList(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-02 请求同步密钥, 请求参数: {}", request);
        return accountAppService.requestKeyList(parseBizData(request, RequestKeyListReqDTO.class));
    }

    @PostMapping({"/ci/app/requestAddPayChannel", "/app/requestAddPayChannel"})
    public RequestAddPayChannelResult requestAddPayChannel(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-23 请求添加支付通道, 请求参数: {}", request);
        return accountAppService.requestAddPayChannel(parseBizData(request, RequestAddPayChannelReqDTO.class));
    }

    @PostMapping({"/ci/app/requestAgreeRelease", "/app/requestAgreeRelease"})
    public RequestRemovePayChannelResult requestAgreeRelease(@ModelAttribute ItpCommonFormRequest request) {
        RequestRemovePayChannelReqDTO bizData = parseBizData(request, RequestRemovePayChannelReqDTO.class);
        log.info("钱包 requestAgreeRelease 解绑支付通道, 请求参数: {}", bizData);
        return accountAppService.requestAgreeRelease(bizData);
    }

    @PostMapping({"/ci/app/requestSetDefaultPayChannel", "/app/requestSetDefaultPayChannel"})
    public RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-24 请求设置默认支付通道, 请求参数: {}", request);
        return accountAppService.requestSetDefaultPayChannel(parseBizData(request, RequestSetDefaultPayChannelReqDTO.class));
    }

    @PostMapping({"/ci/app/requestUpdateChannelDefaultContract", "/app/requestUpdateChannelDefaultContract"})
    public RequestUpdateChannelDefaultContractResult requestUpdateChannelDefaultContract(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-77 更换第三方渠道码默认支付方式, 请求参数: {}", request);
        return accountAppService.requestUpdateChannelDefaultContract(parseBizData(request, RequestUpdateChannelDefaultContractReqDTO.class));
    }

    @PostMapping({"/ci/app/employeeCard/query", "/app/employeeCard/query"})
    public EmployeeCardQueryResult queryEmployeeCard(@ModelAttribute ItpCommonFormRequest request) {
        EmployeeCardQueryReqDTO bizData = parseBizData(request, EmployeeCardQueryReqDTO.class);
        log.info("员工码信息查询, cardNo={}", bizData.getCardNo());
        return accountAppService.queryEmployeeCard(bizData);
    }

    @PostMapping({"/ci/app/employeeCard/activate", "/app/employeeCard/activate"})
    public CommonResult activateEmployeeCard(@ModelAttribute ItpCommonFormRequest request) {
        EmployeeCardActivateReqDTO bizData = parseBizData(request, EmployeeCardActivateReqDTO.class);
        log.info("请求电子员工卡激活或禁用, cardNo={}, actionFlag={}", bizData.getCardNo(), bizData.getActionFlag());
        return accountAppService.activateEmployeeCard(bizData);
    }

    @PostMapping({"/ci/app/requestRemovePayChannel", "/app/requestRemovePayChannel"})
    public RequestRemovePayChannelResult requestRemovePayChannel(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-25 删除支付通道, 请求参数: {}", request);
        return accountAppService.requestRemovePayChannel(parseBizData(request, RequestRemovePayChannelReqDTO.class));
    }

    /**
     * IF8A-42 用户销户。
     */
    @PostMapping("/app/cancelAccount")
    public UserCancelResult userCancel(@ModelAttribute ItpCommonFormRequest request) {
        UserCancelReqDTO bizData = parseBizData(request, UserCancelReqDTO.class);
        log.info("IF8A-42 用户销户, 请求参数: {}", bizData);
        return accountAppService.userCancel(bizData);
    }
}
