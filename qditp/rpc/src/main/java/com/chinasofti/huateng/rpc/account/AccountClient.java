package com.chinasofti.huateng.rpc.account;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.app.QueryActiveCardsReqDTO;
import com.chinasofti.huateng.model.app.QueryActiveCardsResult;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractReqDTO;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractResult;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.RegStatExportDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractReqDTO;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractResult;
import com.chinasofti.huateng.model.app.UpdateHceDataReqDTO;
import com.chinasofti.huateng.model.app.UpdateHceDataResult;
import com.chinasofti.huateng.model.app.UserCancelReqDTO;
import com.chinasofti.huateng.model.app.UserCancelResult;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardNotifyReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardNotifyResult;
import com.chinasofti.huateng.model.employee.EmployeeCardActivateReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryResult;
import com.chinasofti.huateng.model.employee.EmployeeInfoUpdateNotifyReqDTO;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.app.SyncPayAccountIdReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;



@Service
public class AccountClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(AccountClient.class);

    public AccountClient(@Value("${service.account.url:http://127.0.0.1:9098}") String baseUrl, @Value("${service.account.openLogger:true}") boolean openLogger, WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }



    public RequestApplicationResult requestApplication(@RequestBody RequestApplicationReqDTO request) {
        String result = postJsonAndGetResponse("/requestApplication", request);
        return JSONUtil.toBean(result, new TypeReference<RequestApplicationResult>() {
        }, true);
    }

    public RequestAddPayChannelResult requestAddPayChannel(@RequestBody RequestAddPayChannelReqDTO request) {
        String result = postJsonAndGetResponse("/requestAddPayChannel", request);
        return JSONUtil.toBean(result, new TypeReference<RequestAddPayChannelResult>() {
        }, true);
    }

    public RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(@RequestBody RequestSetDefaultPayChannelReqDTO request) {
        String result = postJsonAndGetResponse("/requestSetDefaultPayChannel", request);
        return JSONUtil.toBean(result, new TypeReference<RequestSetDefaultPayChannelResult>() {
        }, true);
    }

    public RequestUpdateChannelDefaultContractResult requestUpdateChannelDefaultContract(@RequestBody RequestUpdateChannelDefaultContractReqDTO request) {
        String result = postJsonAndGetResponse("/requestUpdateChannelDefaultContract", request);
        return JSONUtil.toBean(result, new TypeReference<RequestUpdateChannelDefaultContractResult>() {
        }, true);
    }

    public RequestRemovePayChannelResult requestRemovePayChannel(@RequestBody RequestRemovePayChannelReqDTO request) {
        String result = postJsonAndGetResponse("/requestRemovePayChannel", request);
        return JSONUtil.toBean(result, new TypeReference<RequestRemovePayChannelResult>() {
        }, true);
    }

    public RequestRemovePayChannelResult requestAgreeRelease(@RequestBody RequestRemovePayChannelReqDTO request) {
        String result = postJsonAndGetResponse("/requestAgreeRelease", request);
        return JSONUtil.toBean(result, new TypeReference<RequestRemovePayChannelResult>() {
        }, true);
    }

    public QueryUserInfoResult queryUserInfo(@RequestBody QueryUserInfoReqDTO request) {
        String result = postJsonAndGetResponse("/queryUserInfo", request);
        return JSONUtil.toBean(result, new TypeReference<QueryUserInfoResult>() {
        }, true);
    }


    public QueryActiveCardsResult queryActiveCards(@RequestBody QueryActiveCardsReqDTO request) {
        String result = postJsonAndGetResponse("/queryActiveCards", request);
        return JSONUtil.toBean(result, new TypeReference<QueryActiveCardsResult>() {
        }, true);
    }


    public UserCancelResult userCancel(@RequestBody UserCancelReqDTO request) {
        String result = postJsonAndGetResponse("/userCancel", request);
        return JSONUtil.toBean(result, new TypeReference<UserCancelResult>() {
        }, true);
    }


    public QueryPayChannelByContractResult queryPayChannelByContractNo(
            @RequestBody QueryPayChannelByContractReqDTO request) {
        String result = postJsonAndGetResponse("/queryPayChannelByContractNo", request);
        return JSONUtil.toBean(result, new TypeReference<QueryPayChannelByContractResult>() {
        }, true);
    }

    public QueryUserInfoResult queryCardTypeByCardId(String cardId) {
        String result = getAndGetResponse("/queryCardTypeByCardId?cardId=" + cardId, new java.util.HashMap<>());
        return JSONUtil.toBean(result, new TypeReference<QueryUserInfoResult>() {
        }, true);
    }


    public CommonResult syncPayAccountId(@RequestBody SyncPayAccountIdReqDTO request) {
        String result = postJsonAndGetResponse("/internal/payChannel/syncPayAccountId", request);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }


    public UpdateHceDataResult updateHceData(@RequestBody UpdateHceDataReqDTO request) {
        String result = postJsonAndGetResponse("/updateHceData", request);
        return JSONUtil.toBean(result, new TypeReference<UpdateHceDataResult>() {
        }, true);
    }


    public AlipayTripRequestApplicationRespDTO alipayTripRequestApplication(@RequestBody AlipayTripRequestApplicationReqDTO request) {
        String result = postJsonAndGetResponse("/channel/requestApplication", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripRequestApplicationRespDTO>() {
        }, true);
    }

    public EmployeeCardNotifyResult notifyEmployeeCardStatus(@RequestBody EmployeeCardNotifyReqDTO request) {
        String result = postJsonAndGetResponse("/employeeCard/notify", request);
        return JSONUtil.toBean(result, new TypeReference<EmployeeCardNotifyResult>() {
        }, true);
    }

    public EmployeeCardQueryResult queryEmployeeCard(@RequestBody EmployeeCardQueryReqDTO request) {
        String result = postJsonAndGetResponse("/employeeCard/query", request);
        return JSONUtil.toBean(result, new TypeReference<EmployeeCardQueryResult>() {
        }, true);
    }

    public CommonResult activateEmployeeCard(@RequestBody EmployeeCardActivateReqDTO request) {
        String result = postJsonAndGetResponse("/employeeCard/activate", request);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }

    public CommonResult updateEmployeeInfo(@RequestBody EmployeeInfoUpdateNotifyReqDTO request) {
        String result = postJsonAndGetResponse("/employeeCard/updateNotify", request);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }


    public CommonResult updatePhone(String thirdUserId, String newMsisdn) {
        String url = "/updatePhone?thirdUserId=" + thirdUserId + "&newMsisdn=" + newMsisdn;
        String result = getAndGetResponse(url, new java.util.HashMap<>());
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }


    public CommonResult quartzDemo() {
        return quartzDemo(java.util.Collections.emptyMap());
    }


    public CommonResult quartzDemo(java.util.Map<String, String> headers) {
        String path = "/quartzDemo";
        log.info("调用account-server Quartz联调接口 path={}", path);
        String result = postJsonAndGetResponse(path, new java.util.HashMap<>(), headers);
        CommonResult response = JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
        log.info("调用account-server Quartz联调接口返回 path={}, retCode={}, retMsg={}", path,
                response == null ? null : response.getRetCode(), response == null ? null : response.getRetMsg());
        return response;
    }


    public CommonResult compensatePhoneSignSync() {
        return compensatePhoneSignSync(java.util.Collections.emptyMap());
    }


    public CommonResult compensatePhoneSignSync(java.util.Map<String, String> headers) {
        String path = "/phoneSignSyncCompensate";
        log.info("调用account-server签约展示账号补偿接口 path={}", path);
        String result = postJsonAndGetResponse(path, new java.util.HashMap<>(), headers);
        CommonResult response = JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
        log.info("调用account-server签约展示账号补偿接口返回 path={}, retCode={}, retMsg={}", path,
                response == null ? null : response.getRetCode(), response == null ? null : response.getRetMsg());
        return response;
    }


    public List<RegStatExportDTO> listRegStats(String startDate, String endDate) {
        Map<String, String> params = new HashMap<>();
        if (startDate != null && !startDate.isEmpty()) {
            params.put("startDate", startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            params.put("endDate", endDate);
        }
        String result = getAndGetResponse("/page/user/itp/reg-stats", params, new HashMap<>());
        return JSONUtil.parseObj(result).getBeanList("data", RegStatExportDTO.class);
    }
}
