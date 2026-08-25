package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.AccountAppService;
import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
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
import com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 账户及支付相关接口入口。
 *
 * <p>涵盖账号申请、密钥同步、支付通道管理、员工码查询、渠道默认支付方式等功能。
 * 同时支持 {@code /ci/app} 和 {@code /app} 两条路径。</p>
 */
@RestController
public class AppAccountController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppAccountController.class);

    private final AccountAppService accountAppService;

    public AppAccountController(AccountAppService accountAppService) {
        this.accountAppService = accountAppService;
    }

    @PostMapping({"/ci/app/requestApplication", "/app/requestApplication"})
    public RequestApplicationResult requestApplication(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-01 请求开户, 请求参数: {}", request);
        return accountAppService.requestApplication(parseBizData(request, RequestApplicationReqDTO.class));
    }

    @PostMapping({"/ci/app/requestKeyList", "/app/requestKeyList"})
    public RequestKeyListResult requestKeyList(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-02 请求同步密钥, 请求参数: {}", request);
        return accountAppService.requestKeyList(parseBizData(request, RequestKeyListReqDTO.class));
    }

    @PostMapping({"/ci/app/requestAddPayChannel", "/app/requestAddPayChannel"})
    public RequestAddPayChannelResult requestAddPayChannel(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-23 请求添加支付通道, 请求参数: {}", request);
        return accountAppService.requestAddPayChannel(parseBizData(request, RequestAddPayChannelReqDTO.class));
    }

    @PostMapping({"/ci/app/requestSetDefaultPayChannel", "/app/requestSetDefaultPayChannel"})
    public RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-24 请求设置默认支付通道, 请求参数: {}", request);
        return accountAppService.requestSetDefaultPayChannel(parseBizData(request, RequestSetDefaultPayChannelReqDTO.class));
    }

    @PostMapping({"/ci/app/requestUpdateChannelDefaultContract", "/app/requestUpdateChannelDefaultContract"})
    public RequestUpdateChannelDefaultContractResult requestUpdateChannelDefaultContract(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-77 更换第三方渠道码默认支付方式, 请求参数: {}", request);
        return accountAppService.requestUpdateChannelDefaultContract(parseBizData(request, RequestUpdateChannelDefaultContractReqDTO.class));
    }

    @PostMapping({"/ci/app/employeeCard/query", "/app/employeeCard/query"})
    public EmployeeCardQueryResult queryEmployeeCard(@ModelAttribute CommonFormRequest request) {
        EmployeeCardQueryReqDTO bizData = parseBizData(request, EmployeeCardQueryReqDTO.class);
        log.info("员工码信息查询, cardNo={}", bizData.getCardNo());
        return accountAppService.queryEmployeeCard(bizData);
    }

    @PostMapping({"/ci/app/requestRemovePayChannel", "/app/requestRemovePayChannel"})
    public RequestRemovePayChannelResult requestRemovePayChannel(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-25 删除支付通道, 请求参数: {}", request);
        return accountAppService.requestRemovePayChannel(parseBizData(request, RequestRemovePayChannelReqDTO.class));
    }
}
