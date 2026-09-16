package com.chinasofti.huateng.rpc.account;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
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

/**
 * @author zzm
 * @date 2026/5/13 11:01
 */

@Service
public class AccountClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(AccountClient.class);

    public AccountClient(@Value("${service.account.url:http://127.0.0.1:9098}") String baseUrl, @Value("${service.account.openLogger:true}") boolean openLogger, WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * account 注册
     *
     * @return
     */

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

    /**
     * IF8A-42 用户销户：把 {@code USER_ITP_REG_INFO} 该用户全部有效票卡置为已注销。
     *
     * <p>只改开户记录，<b>不动 {@code USER_PAY_CHANNEL}</b>。APP 顺序是 35 → 42 → 75，
     * 支付渠道由随后的 IF8A-75 逐个解绑，此处提前删会让解绑拿不到渠道信息。</p>
     */
    public UserCancelResult userCancel(@RequestBody UserCancelReqDTO request) {
        String result = postJsonAndGetResponse("/userCancel", request);
        return JSONUtil.toBean(result, new TypeReference<UserCancelResult>() {
        }, true);
    }

    /**
     * 按签约流水号查询支付通道（只读）。
     *
     * <p>IF8A-75 补建解约申请前用它取 {@code cardId} / {@code cardType}：
     * {@code APP_PAY_SIGN_INFO} 的这两列全库为 NULL，真实来源是 account 侧的
     * {@code APP_USER_PAY_CHANNEL}，其 {@code REQ_CONTRACT_NO} 即签约流水号。</p>
     *
     * <p>未找到时返回 {@code retCode=8004}，调用方 MUST 判 retCode 而不是只判字段空。</p>
     */
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

    /**
     * 支付域签约成功后，把 {@code PAY_ACCOUNT_ID} 推给账户域（ADR-D32）。
     *
     * <p>补的是 ADR-D30 的覆盖率缺口：账户域 {@code APP_USER_PAY_CHANNEL.PAY_ACCOUNT_ID}
     * 原先只有 IF8A-77 会写，用户签约后未走 IF8A-77 时该列恒为空。</p>
     *
     * <p><b>调用方 MUST 把它当「允许失败」处理</b>：本方法只回写一列展示值，
     * 权威值始终在支付域 {@code APP_PAY_SIGN_INFO}，丢一次不影响任何业务动作
     * （IF8A-77 仍会自行向支付域取值）。因此调用点 MUST catch 全部异常只记日志，
     * <b>NEVER 让它把已提交的签约结果翻成失败</b>。</p>
     *
     * <p>命中 0 行时账户域返回 {@code retCode=8004} 而不是抛错——签约先于开卡的时序是合法的。
     * 按 AGENTS.md §5.2，调用方 <b>MUST 判 retCode，NEVER 假定「没抛异常就是写成功」</b>。</p>
     */
    public CommonResult syncPayAccountId(@RequestBody SyncPayAccountIdReqDTO request) {
        String result = postJsonAndGetResponse("/internal/payChannel/syncPayAccountId", request);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }

    /**
     * 回写 IF1A-01 reserve1 中的 HCE 卡数据。
     */
    public UpdateHceDataResult updateHceData(@RequestBody UpdateHceDataReqDTO request) {
        String result = postJsonAndGetResponse("/updateHceData", request);
        return JSONUtil.toBean(result, new TypeReference<UpdateHceDataResult>() {
        }, true);
    }

    /**
     * 支付宝出行-开卡申请。
     */
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

    /**
     * 更换手机号。
     */
    public CommonResult updatePhone(String thirdUserId, String newMsisdn) {
        String url = "/updatePhone?thirdUserId=" + thirdUserId + "&newMsisdn=" + newMsisdn;
        String result = getAndGetResponse(url, new java.util.HashMap<>());
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }

    /**
     * 调用 account-server 的 Quartz 联调接口。
     *
     * <p>该接口只用于验证 web-server Quartz 到 account-server 的 RPC 调用链，
     * 不会修改账户业务数据。</p>
     *
     * @return account-server 的处理结果
     */
    public CommonResult quartzDemo() {
        return quartzDemo(java.util.Collections.emptyMap());
    }

    /**
     * 带 trace 头的重载，供 web-admin 的 Quartz 任务调用。
     *
     * <p>headers 由 {@code QuartzTraceUtils.traceHeaders} 生成（W3C {@code traceparent} +
     * {@code X-Vlogs-Capture}）。**NEVER 传自定义 {@code traceId} 头**——micro 的 {@code FirstFilter}
     * 会把请求头 key 全部小写后塞进 MDC，与 Micrometer 写入的 traceId 抢同一个键。</p>
     *
     * <p>⚠️ {@code ProxyWebClient} 只自动透传 MDC 里的 {@code authorization}，**不会**注入任何 trace 头，
     * 因此调用方 MUST 显式传入。</p>
     */
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

    /**
     * 触发 account-server 的「手机号变更后显示账号同步到支付域」补偿扫表重推。
     *
     * <p>无入参：扫描范围由 account-server 自行决定，本方法只负责触发。
     * 重复触发是安全的（下游用 CAS 兜幂等）。</p>
     */
    public CommonResult compensatePhoneSignSync() {
        return compensatePhoneSignSync(java.util.Collections.emptyMap());
    }

    /**
     * 带 trace 头的重载，供 web-admin 的 Quartz 任务调用。
     *
     * <p>headers 由 {@code QuartzTraceUtils.traceHeaders} 生成。{@code ProxyWebClient}
     * 只自动透传 MDC 里的 {@code authorization}、**不会**注入任何 trace 头，
     * 因此调用方 MUST 显式传入，否则 traceId 断链、前台「执行日志」查不到下游。</p>
     */
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

}
