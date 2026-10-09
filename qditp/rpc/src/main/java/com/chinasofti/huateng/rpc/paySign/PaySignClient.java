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
import com.chinasofti.huateng.model.app.ReceiveRefundResultReqDTO;
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
import com.chinasofti.huateng.model.paysign.RegisterCompletedPayTxnReqDTO;
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
 * @date 2026/5/13 11:01。
 * @author zzm。
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
     * 请求签约信息。
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
     * 支付 API §5.2 退款回调内部转发（2026-09-22 新增，P1-3）。
     *
     * <p>与 {@link #receivePayResult} 是**两条独立回调**，NEVER 合并：§5.1 送的是支付结果，
     * §5.2 送的是退款结果，报文字段与落库表都不同。
     */
    public PaySignCallbackResult receiveRefundResult(@RequestBody ReceiveRefundResultReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/receiveRefundResult", request);
        return JSONUtil.toBean(result, new TypeReference<PaySignCallbackResult>() {
        }, true);
    }

    /**
     * IF8A-06 请求解约。
     * @param request 请求解约业务参数。
     * @return 请求解约受理结果。
     */    public RequestTerminationResult requestTermination(@RequestBody RequestTerminationReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTermination", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTerminationResult>() {
        }, true);
    }

    /**
     * IF8A-75 直接解绑支付方式。
     * @param request 直接解绑业务参数。
     * @return 发起结果。
     */
    public UnbindAgreementResult unbindAgreement(@RequestBody UnbindAgreementReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/unbindAgreement", request);
        return JSONUtil.toBean(result, new TypeReference<UnbindAgreementResult>() {
        }, true);
    }

    /**
     * 支付 API 1.1 请求支付。
     * @param request 请求支付业务参数。
     * @return 请求支付结果。
     */
    public RequestPayResult requestPay(@RequestBody RequestPayReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestPay", request);
        return JSONUtil.toBean(result, new TypeReference<RequestPayResult>() {
        }, true);
    }

    /**
     * 支付 API 3.1 请求退款。
     */
    public RequestRefundResult requestRefund(@RequestBody RequestRefundReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestRefund", request);
        return JSONUtil.toBean(result, new TypeReference<RequestRefundResult>() {
        }, true);
    }

    /**
     * 支付 API 5.3 解约回调内部转发。
     * @param request 解约回调业务参数。
     * @return 回调处理结果。
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
     */
    public ProcessTerminationRespDTO processTermination(@RequestBody ProcessTerminationReqDTO request) {
        return processTermination(request, null);
    }

    /**
     * 带自定义请求头的重载，用于把链路追踪上下文传给 pay-sign。
     */
    public ProcessTerminationRespDTO processTermination(ProcessTerminationReqDTO request, Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/termination/process", request, headers);
        return JSONUtil.toBean(result, new TypeReference<ProcessTerminationRespDTO>() {
        }, true);
    }

    /**
     * 签约结果通知补偿（内部接口 /internal/paySign/compensateNotify）。
     */
    public CompensateNotifyRespDTO compensateSignNotify(Map<String, String> headers) {
        // bodyValue(requestBody)，Spring 的 BodyInserters.fromValue 断言非 null，传 null 会抛
        // IllegalArgumentException: 'body' must not be null，请求根本发不出去。传空 Map 序列化成 {}。
        String result = postJsonAndGetResponse("/internal/paySign/compensateNotify", new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<CompensateNotifyRespDTO>() {
        }, true);
    }

    /**
     * 解约结果通知补偿（内部接口 /internal/termination/compensateNotify）。
     */
    public CompensateNotifyRespDTO compensateTerminationNotify(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/termination/compensateNotify", new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<CompensateNotifyRespDTO>() {
        }, true);
    }

    /**
     * 退款回查补偿（内部接口 /internal/payment/compensateRefundQuery）。
     */
    public CompensateNotifyRespDTO compensateRefundQuery(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/payment/compensateRefundQuery", new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<CompensateNotifyRespDTO>() {
        }, true);
    }

    /**
     * 退款汇总跨表对账补偿（内部接口 /internal/payment/compensateRefundSummary）。
     */
    public CompensateNotifyRespDTO compensateRefundSummary(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/payment/compensateRefundSummary", new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<CompensateNotifyRespDTO>() {
        }, true);
    }

    /**
     * 登记一条「已完成、不经支付中心」的支付流水（内部接口 /internal/payment/registerCompletedTxn）。
     *
     * <p>用于 BOM 补站（{@code adviceOpt} 005 / 006 / 020）这类「现场已收款、ITP 不扣款」的订单：
     * 它们走不到 {@code requestPay}，但用户 2026-09-22 裁决「没有 {@code PAY_TXN_DETAIL} 行就不是完整订单」。
     *
     * <p><b>调用方 MUST 在落单之前调本方法、失败即整笔失败</b>（用户 2026-09-22 选定强一致口径）：
     * 顺序颠倒会留下「订单已 SUCCESS、流水缺行」，而这正是本次要消灭的状态；
     * 本方法按 {@code UK_PAY_TXN_DETAIL_ORDER} 幂等，重试安全。
     *
     * @return {@code Ok} 已登记（含「本来就有」）；{@code BizRejected} 参数或口径被拒、重推无用；
     *         {@code Unreachable} 未获业务答复、可重试。
     */
    public RpcOutcome registerCompletedTxn(@RequestBody RegisterCompletedPayTxnReqDTO request) {
        String result;
        try {
            result = postJsonAndGetResponse("/internal/payment/registerCompletedTxn", request);
        } catch (Exception e) {
            log.warn("登记已完成支付流水未获业务答复（可重试）, orderNo={}", request.getOrderNo(), e);
            return new RpcOutcome.Unreachable(e);
        }
        if (result == null || result.isEmpty()) {
            return new RpcOutcome.BizRejected(null, "支付域响应体为空");
        }
        try {
            cn.hutool.json.JSONObject wrapper = JSONUtil.parseObj(result);
            return RpcOutcome.ofRetCode(wrapper.getStr("retCode"), wrapper.getStr("retMsg"));
        } catch (Exception e) {
            log.warn("登记已完成支付流水响应体非JSON（按业务拒绝处理，重推同一报文不会变好）, orderNo={}, result={}",
                    request.getOrderNo(), result, e);
            return new RpcOutcome.BizRejected(null, "支付域响应体非JSON");
        }
    }

    /**
     * 更新用户签约展示账号（如更换手机号时同步更新）。
     * @deprecated 请改用 {@link #updateDisplayAccountOutcome(String, String)}。boolean 把「支付域业务拒绝」。
     */
    @Deprecated
    public boolean updatePaySignDisplayAccount(String thirdUserId, String displayAccount) {
        return updateDisplayAccountOutcome(thirdUserId, displayAccount).isOk();
    }

    /**
     * 更新用户签约展示账号，返回可区分「业务拒绝 / 不可达」的三态结果。
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
