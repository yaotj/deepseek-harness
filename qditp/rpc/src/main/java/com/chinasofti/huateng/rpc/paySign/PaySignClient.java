package com.chinasofti.huateng.rpc.paySign;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultResult;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.app.RequestPayTxnBatchResult;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationResult;
import com.chinasofti.huateng.model.app.UnbindAgreementReqDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.PaySignInfoDTO;
import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationReqDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * @author zzm
 * @date 2026/5/13 11:01
 */

@Service
public class PaySignClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(PaySignClient.class);

    public PaySignClient(@Value("${service.paySign.url:pay-sign-service}") String baseUrl, @Value("${service.paySign.openLogger:true}") boolean openLogger, WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    @Override
    protected Duration getResponseTimeout() {
        return Duration.ofSeconds(30);
    }

    /**
     * 请求签约信息
     *
     * @return
     */

    public RequestSignInfoResult requestSignInfo(@RequestBody RequestSignInfoReqDTO request) {
        String result = postJsonAndGetResponse("/requestSignInfo", request);
        return JSONUtil.toBean(result, new TypeReference<RequestSignInfoResult>() {
        }, true);
    }

    public PaySignCallbackResult receiveSignResult(@RequestBody ReceiveSignResultReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/receiveSignResult", request);
        return JSONUtil.toBean(result, new TypeReference<PaySignCallbackResult>() {
        }, true);
    }

    /**
     * 支付 API 5.1 支付回调内部转发。
     */
    public PaySignCallbackResult receivePayResult(@RequestBody ReceivePayResultReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/receivePayResult", request);
        return JSONUtil.toBean(result, new TypeReference<PaySignCallbackResult>() {
        }, true);
    }

    /**
     * IF8A-06 请求解约。
     *
     * <p>fep-app 通过该 RPC 调用 pay-sign-server，由 pay-sign-server 组装支付平台 2.3 请求解约报文。</p>
     *
     * @param request 请求解约业务参数
     * @return 请求解约受理结果
     */
    public RequestTerminationResult requestTermination(@RequestBody RequestTerminationReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTermination", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTerminationResult>() {
        }, true);
    }

    /**
     * IF8A-75 直接解绑支付方式。
     *
     * <p>与 {@link #requestTermination} 的区别：本接口**立即**向支付中心发起解约，
     * 不等账期结束后的扫表任务。应答 0000 只表示「已向支付渠道发起」，不等于已解绑完成。</p>
     *
     * @param request 直接解绑业务参数
     * @return 发起结果
     */
    public UnbindAgreementResult unbindAgreement(@RequestBody UnbindAgreementReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/unbindAgreement", request);
        return JSONUtil.toBean(result, new TypeReference<UnbindAgreementResult>() {
        }, true);
    }

    /**
     * 支付 API 1.1 请求支付。
     *
     * <p>调用方只传业务参数，pay-sign-server 负责组装支付网关公共参数和签名。</p>
     *
     * @param request 请求支付业务参数
     * @return 请求支付结果
     */
    public RequestPayResult requestPay(@RequestBody RequestPayReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestPay", request);
        return JSONUtil.toBean(result, new TypeReference<RequestPayResult>() {
        }, true);
    }

    /**
     * 支付 API 3.1 请求退款。
     *
     * <p>调用方只传 orderNo/refundAmount，pay-sign-server 负责补齐退款请求参数和签名。</p>
     */
    public RequestRefundResult requestRefund(@RequestBody RequestRefundReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestRefund", request);
        return JSONUtil.toBean(result, new TypeReference<RequestRefundResult>() {
        }, true);
    }

    /**
     * 支付 API 5.3 解约回调内部转发。
     *
     * <p>支付平台先回调 fep-app，fep-app 再通过该 RPC 透传给 pay-sign-server 完成本地解约业务。</p>
     *
     * @param request 解约回调业务参数
     * @return 回调处理结果
     */
    public PaySignCallbackResult receiveTerminationResult(@RequestBody ReceiveTerminationResultReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/receiveTerminationResult", request);
        return JSONUtil.toBean(result, new TypeReference<PaySignCallbackResult>() {
        }, true);
    }

    /**
     * 支付宝出行-添加签约信息。
     */
    public AlipayTripAddContractRespDTO alipayTripAddContract(@RequestBody AlipayTripAddContractReqDTO request) {
        String result = postJsonAndGetResponse("/channel/addContract", request);
        RequestSignInfoResult appResult = JSONUtil.toBean(result, new TypeReference<RequestSignInfoResult>() {
        }, true);
        AlipayTripAddContractRespDTO response = new AlipayTripAddContractRespDTO();
        response.setRetCode(appResult.getRetCode());
        response.setRetMsg(appResult.getRetMsg());
        return response;
    }

    /**
     * 支付宝出行-解约登记。
     */
    public AlipayTripTerminateContractRespDTO alipayTripTerminateContract(@RequestBody AlipayTripTerminateContractReqDTO request) {
        RequestTerminationReqDTO appRequest = new RequestTerminationReqDTO();
        appRequest.setRequestSignSeq(request.getAgreementCode());
        RequestTerminationResult appResult = requestTermination(appRequest);
        AlipayTripTerminateContractRespDTO response = new AlipayTripTerminateContractRespDTO();
        response.setRetCode(appResult.getRetCode());
        response.setRetMsg(appResult.getRetMsg());
        return response;
    }

    /**
     * 根据签约流水号查询签约信息。
     */
    public PaySignInfoDTO querySignInfoBySeq(String requestSignSeq) {
        String result = getAndGetResponse("/querySignInfoBySeq?requestSignSeq=" + requestSignSeq, new java.util.HashMap<>());
        return JSONUtil.toBean(result, PaySignInfoDTO.class, true);
    }

    /**
     * IF8A-22 签约结果查询。
     */
    public RequestContractResultResult requestContractResult(RequestContractResultReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestContractResult", request);
        return JSONUtil.toBean(result, RequestContractResultResult.class, true);
    }

    /**
     * IF8A-05 批量查询支付明细（供 ticket-server 双源合并）。
     */
    public RequestPayTxnBatchResult queryPayTxnBatch(@RequestBody com.chinasofti.huateng.model.app.QueryPayTxnBatchReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/queryPayTxnBatch", request);
        RequestPayTxnBatchResult batchResult = JSONUtil.toBean(result, new TypeReference<RequestPayTxnBatchResult>() {
        }, true);
        if (batchResult == null) {
            RequestPayTxnBatchResult errorResult = new RequestPayTxnBatchResult();
            errorResult.setRetCode("9999");
            errorResult.setRetMsg("批量查询支付明细响应为空");
            return errorResult;
        }
        // 如果 retCode 不是 0000，统一返回 9999，并将原始 retCode 追加到 retMsg 中便于排查
        if (!"0000".equals(batchResult.getRetCode())) {
            String originalRetCode = batchResult.getRetCode();
            batchResult.setRetCode("9999");
            String originalRetMsg = batchResult.getRetMsg();
            if (originalRetMsg == null || originalRetMsg.isEmpty()) {
                batchResult.setRetMsg("批量查询支付明细失败 [" + originalRetCode + "]");
            } else {
                batchResult.setRetMsg(originalRetMsg + " [" + originalRetCode + "]");
            }
        }
        return batchResult;
    }

    /**
     * 解约申请批处理（内部接口 /internal/termination/process）。
     *
     * <p>供 web-server 的 Quartz 定时任务调用：扫 PENDING 发起支付平台解约、扫 SCANNING 主动查询收口。
     * request 传 {@code referenceTime} / {@code delayDays} 时只处理满 N 天的申请，
     * 传 null 则不按申请时间过滤。</p>
     *
     * <p>调用方 **MUST** 检查返回值：本方法不抛业务异常，失败体现在 resultCode 上；
     * 响应为 null 说明 HTTP 层就没通。</p>
     */
    public ProcessTerminationRespDTO processTermination(@RequestBody ProcessTerminationReqDTO request) {
        return processTermination(request, null);
    }

    /**
     * 带自定义请求头的重载，用于把链路追踪上下文传给 pay-sign。
     *
     * <p>headers 传 {@code traceparent}（W3C 格式 {@code 00-<32位hex>-<16位hex>-00}）即可，
     * pay-sign 侧由 Spring Boot tracing 自动解析并写入 MDC 的 traceId / spanId。</p>
     *
     * <p>**NEVER 传自定义的 traceId 头**：pay-sign 的 {@code FirstFilter} 会把请求头全部转小写后放入 MDC，
     * 落进去的 key 是 traceid，与日志 pattern 取的 traceId 不匹配，输出恒为空。</p>
     */
    public ProcessTerminationRespDTO processTermination(ProcessTerminationReqDTO request, Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/termination/process", request, headers);
        return JSONUtil.toBean(result, new TypeReference<ProcessTerminationRespDTO>() {
        }, true);
    }

    /**
     * 签约结果通知补偿（内部接口 /internal/paySign/compensateNotify）。
     *
     * <p>扫 APP_PAY_SIGN_REQUEST 中 OPERATION_TYPE=RECEIVE_SIGN_RESULT、未超重试上限、
     * NOTIFY_STATUS=FAILED 或 PENDING 滞留超阈值的记录，重新向 APP 推送签约结果。</p>
     *
     * <p><b>调用方 NEVER 在同一次调度内循环调用本接口。</b>下游在提交重发**之前**就同步
     * 递增 NOTIFY_RETRY_COUNT 并把状态置为 FAILED，而真正的通知是异步发出的；
     * 紧接着再调一轮会立刻扫到同一批（计数已 +1、结果还没回写），几秒内烧完全部重试预算。
     * 排空要靠 cron 周期，调度间隔 MUST 大于下游的 PENDING 滞留阈值。</p>
     *
     * <p>返回的 submitted 只代表「提交成功」，NEVER 用它判断通知是否送达，真实结果看
     * APP_PAY_SIGN_REQUEST 的 NOTIFY_STATUS / NOTIFY_RESULT；响应为 null 说明 HTTP 层就没通。</p>
     */
    public CompensateNotifyRespDTO compensateSignNotify(Map<String, String> headers) {
        // 下游是无参 POST，但 body MUST NOT 传 null：ProxyWebClient.postJsonAndGetResponse 无条件调
        // bodyValue(requestBody)，Spring 的 BodyInserters.fromValue 断言非 null，传 null 会抛
        // IllegalArgumentException: 'body' must not be null，请求根本发不出去。传空 Map 序列化成 {}。
        String result = postJsonAndGetResponse("/internal/paySign/compensateNotify", new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<CompensateNotifyRespDTO>() {
        }, true);
    }

    /**
     * 解约结果通知补偿（内部接口 /internal/termination/compensateNotify）。
     *
     * <p>扫的是 APP_TERMINATION_REQUEST，与 {@link #compensateSignNotify(Map)} 覆盖的表不同，
     * 两者**不可互相替代**，MUST 各自调度。</p>
     *
     * <p>同样 NEVER 在单次调度内循环调用，原因见 {@link #compensateSignNotify(Map)}。</p>
     */
    public CompensateNotifyRespDTO compensateTerminationNotify(Map<String, String> headers) {
        // body MUST NOT 传 null，原因见 compensateSignNotify。
        String result = postJsonAndGetResponse("/internal/termination/compensateNotify", new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<CompensateNotifyRespDTO>() {
        }, true);
    }

    /**
     * 退款回查补偿（内部接口 /internal/payment/compensateRefundQuery）。
     *
     * <p>扫 {@code PAY_REFUND_DETAIL} 中停在 {@code PROCESSING}、距上次发起退款已超
     * {@code REFUND_QUERY_STALE_MINUTES}（pay-sign 侧当前为 5 分钟）的退款单，逐条<b>出网</b>调
     * 支付中心 §3.2 refundQuery 回查并 CAS 收口，收口成功后重算原支付订单的退款汇总。</p>
     *
     * <p><b>它与 {@link #compensateRefundSummary(Map)} 是两件不同的事，NEVER 合并调度</b>：
     * 本方法<b>会出网</b>、要贴着退款时效跑；那个<b>不出网</b>、只重算本地两表汇总，属日终级别。
     * 合并后既没法分别调频，出网这半边一挂还会把纯本地的那半边一起拖停。</p>
     *
     * <p><b>调用方 NEVER 在同一次调度内循环调用本接口。</b>下游按「距上次发起超 5 分钟」筛选，
     * 紧接着再调一轮只会扫到同一批还没到窗口的单子，白跑一遍并重复出网。
     * 因此<b>调度间隔 MUST 大于下游的 staleMinutes（5 分钟）</b>，建议 {@code 0 0/10 * * * ?}。</p>
     *
     * <p>返回的 submitted 只代表「本轮提交了几笔回查」，<b>NEVER 用它判断某一笔的结果</b>，
     * 单条是否收口 MUST 看 {@code PAY_REFUND_DETAIL.REFUND_STATUS}；响应为 null 说明 HTTP 层就没通。</p>
     */
    public CompensateNotifyRespDTO compensateRefundQuery(Map<String, String> headers) {
        // 下游是无参 POST，但 body MUST NOT 传 null，原因见 compensateSignNotify 内的注释。
        String result = postJsonAndGetResponse("/internal/payment/compensateRefundQuery", new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<CompensateNotifyRespDTO>() {
        }, true);
    }

    /**
     * 退款汇总跨表对账补偿（内部接口 /internal/payment/compensateRefundSummary）。
     *
     * <p><b>本方法不出网</b>：下游只比对 {@code PAY_REFUND_DETAIL}（唯一账本）与
     * {@code PAY_TXN_DETAIL} 的 {@code REFUND_AMOUNT} / {@code REFUND_STATUS} 两列汇总，
     * 对不一致的原支付订单逐单重算，不会调支付中心。与 {@link #compensateRefundQuery(Map)}
     * 覆盖的场景不同，两者<b>不可互相替代</b>，MUST 各自调度、各自定频。</p>
     *
     * <p><b>返回的 skipped 长期非 0 是预期，不代表本轮失败。</b>它混着两类单：一类是重算影响 0 行
     * （下一轮重扫即可自愈），另一类是「明细已 SUCCESS 但 {@code PAY_TXN_DETAIL} 里没有该 ORDER_NO」，
     * 这类<b>永远不会自愈</b>、只打 WARN 等人工核对。因此调用方 MUST NOT 把 skipped &gt; 0 记成 ERROR，
     * 区分两类只能看 pay-sign 侧日志措辞。</p>
     *
     * <p>同样 NEVER 用 submitted 判断某一笔的结果，单条汇总以 {@code PAY_TXN_DETAIL} 实际两列为准；
     * 响应为 null 说明 HTTP 层就没通。</p>
     */
    public CompensateNotifyRespDTO compensateRefundSummary(Map<String, String> headers) {
        // body MUST NOT 传 null，原因见 compensateSignNotify。
        String result = postJsonAndGetResponse("/internal/payment/compensateRefundSummary", new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<CompensateNotifyRespDTO>() {
        }, true);
    }

    /**
     * 更新用户签约展示账号（如更换手机号时同步更新）。
     *
     * @deprecated 请改用 {@link #updateDisplayAccountOutcome(String, String)}。boolean 把「支付域业务拒绝」
     * 与「网络不可达」压成同一个 false，前者重推永远不会成功、后者才该进补偿队列；
     * 这条方法本身也是 AGENTS.md §5.2 点名的两处之一。<b>本方法保留只为不改签名</b>
     * （`rpc` 版本号锁死、被 21 个模块引用），实现已委托给新方法，<b>NEVER 在此重复一份解析逻辑</b>。
     */
    @Deprecated
    public boolean updatePaySignDisplayAccount(String thirdUserId, String displayAccount) {
        return updateDisplayAccountOutcome(thirdUserId, displayAccount).isOk();
    }

    /**
     * 更新用户签约展示账号，返回可区分「业务拒绝 / 不可达」的三态结果。
     *
     * <p>支付域在 {@code APP_PAY_SIGN_INFO} UPDATE 影响 0 行时就返 FAIL
     * （{@code PaySignAppController:159}），即「该用户没有签约记录」也走这条 —— 那属
     * {@link RpcOutcome.BizRejected}，<b>MUST 直接转终态 / 工单，NEVER 反复重推</b>。</p>
     *
     * <p>本方法<b>NEVER 向外抛异常</b>：异常统一收成 {@link RpcOutcome.Unreachable} 并带上原始 cause。
     * 与旧 boolean 版的区别只在于「失败的原因不再丢失」，调用方仍 MUST 自行决定抛不抛。</p>
     */
    public RpcOutcome updateDisplayAccountOutcome(String thirdUserId, String displayAccount) {
        String url = "/ci/app/updateDisplayAccount?thirdUserId=" + thirdUserId + "&displayAccount=" + displayAccount;
        String result;
        try {
            result = getAndGetResponse(url, new java.util.HashMap<>());
        } catch (Exception e) {
            log.warn("更新签约展示账号未获业务答复（可重试）, thirdUserId={}", thirdUserId, e);
            return new RpcOutcome.Unreachable(e);
        }
        if (result == null || result.isEmpty()) {
            return new RpcOutcome.BizRejected(null, "支付域响应体为空");
        }
        // 解析 MUST 与上面的调用一样收在本方法内：HTTP 已 2xx 但响应体不是 JSON（如网关返回 HTML 错误页）时
        // JSONUtil.parseObj 会抛 JSONException，逃出去就打破了本方法「NEVER 向外抛异常」的契约 ——
        // 同步链路会退化成全局异常处理器的 UUID retCode，补偿链路则因状态与重试次数都不落库而永远没有出口。
        try {
            cn.hutool.json.JSONObject wrapper = JSONUtil.parseObj(result);
            return RpcOutcome.ofRetCode(wrapper.getStr("retCode"), wrapper.getStr("retMsg"));
        } catch (Exception e) {
            log.warn("更新签约展示账号响应体非JSON（按业务拒绝处理，重推同一报文不会变好）, thirdUserId={}, result={}",
                    thirdUserId, result, e);
            return new RpcOutcome.BizRejected(null, "支付域响应体非JSON");
        }
    }
}
