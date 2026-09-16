package com.chinasofti.huateng.rpc.collectpay;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.collectpay.AppPayOrderCloseReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderQueryReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderRegisterReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderResultRespDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * collect-pay-server RPC client.
 */
@Service
public class CollectPayClient extends ProxyWebClient {

    public CollectPayClient(@Value("${service.collectPay.url:collect-pay-service}") String baseUrl,
                            @Value("${service.collectPay.openLogger:true}") boolean openLogger,
                            WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * Forwards the APP order form unchanged to collect-pay-server.
     */
    public JSONObject requestOrder(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestOrder", formData);
        return JSON.parseObject(result);
    }

    /**
     * Forwards the APP payment-info form unchanged to collect-pay-server.
     */
    public JSONObject requestPaymentInfo(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestPaymentInfo", formData);
        return JSON.parseObject(result);
    }

    /**
     * Forwards the APP payment-result query form unchanged to collect-pay-server.
     */
    public JSONObject requestPayResult(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestPayResult", formData);
        return JSON.parseObject(result);
    }

    /**
     * Forwards the APP request-pay-order form unchanged to collect-pay-server.
     */
    public JSONObject requestPayOrder(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestPayOrder", formData);
        return JSON.parseObject(result);
    }

    public JSONObject requestRefundTicket(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestRefundTicket", formData);
        return JSON.parseObject(result);
    }

    public JSONObject requestRefundTicketResult(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestRefundTicketResult", formData);
        return JSON.parseObject(result);
    }

    public JSONObject requestPreActiveOrderList(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestPreActiveOrderList", formData);
        return JSON.parseObject(result);
    }

    public JSONObject requestActiveTicket(Map<String, String> formData) {
        String result = postFormAndGetResponse("/itptvm/ci/tvm/requestActiveTicket", formData);
        return JSON.parseObject(result);
    }

    public JSONObject receiveRefundResult(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/receiveRefundResult", formData);
        return JSON.parseObject(result);
    }

    // ==================== /internal/app-order（APP 订单表写入收口，2026-09-14） ====================

    /**
     * 把一张外部单据登记成 APP 订单行 + 支付前置单行，让乘客能走 collect-pay 的收银台把它付掉。
     *
     * <p><b>幂等键是 {@code orderNo}</b>：对端按订单号回查，已登记过就直接返成功，
     * 因此本方法可被补偿任务无限次重放。这一点是「先落本地 PENDING、提交后再同步」
     * 那套 outbox 能成立的前提，<b>NEVER</b> 让对端改成「重复即报错」。</p>
     *
     * <p>返回 {@code RpcOutcome} 而不是 boolean（AGENTS.md §5.2）：
     * {@link RpcOutcome.BizRejected} 重推一万次也不会成功、MUST 一次即终态 + 落 ERROR；
     * 只有 {@link RpcOutcome.Unreachable} 才该进补偿队列。</p>
     */
    public RpcOutcome registerAppPayOrder(AppPayOrderRegisterReqDTO request) {
        String response;
        try {
            response = postJsonAndGetResponse("/internal/app-order/register", request);
        } catch (Exception e) {
            return new RpcOutcome.Unreachable(e);
        }
        return parseOutcome(response);
    }

    /**
     * 按订单号把 APP 订单表上仍待支付的行置为支付失败（对端带 {@code PAY_STATUS='0'} 白名单）。
     *
     * <p>天然幂等：已支付 / 已失败的行在对端影响 0 行，仍返成功 —— 关单的语义是
     * 「保证乘客付不了」，行本来就付不了时目标已达成。</p>
     */
    public RpcOutcome closeUnpaidAppPayOrder(AppPayOrderCloseReqDTO request) {
        String response;
        try {
            response = postJsonAndGetResponse("/internal/app-order/close-unpaid", request);
        } catch (Exception e) {
            return new RpcOutcome.Unreachable(e);
        }
        return parseOutcome(response);
    }

    /**
     * 按订单号回查 APP 订单表的支付结果。
     *
     * <p><b>本方法在网络失败时 MUST 抛异常、NEVER 返回「查不到」</b>：
     * {@code found=false} 是对端答复的业务事实（这张单没登记进 APP 订单表，乘客根本付不了），
     * 调用方会据此落 ERROR 或补登记；而连不上时我方对支付状态一无所知，
     * 若也返回 {@code found=false}，一次抖动就会被误判成「单据丢失」。
     * 两者的处置方向相反，因此 <b>NEVER</b> 在这里 catch 成空对象。</p>
     */
    public AppPayOrderResultRespDTO queryAppPayOrderResult(AppPayOrderQueryReqDTO request) {
        String response = postJsonAndGetResponse("/internal/app-order/pay-result", request);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("collect-pay 回查 APP 订单支付结果返回空响应, orderNo="
                    + (request == null ? null : request.getOrderNo()));
        }
        return JSON.parseObject(response, AppPayOrderResultRespDTO.class);
    }

    /**
     * 把对端应答的 {@code retCode} 归成 {@code RpcOutcome}。
     *
     * <p>空响应 / 缺 {@code retCode} 归 {@code BizRejected} 而不是 {@code Unreachable}：
     * 那时 HTTP 已经 2xx，是对端契约问题，重推同一报文不会变好（见 {@link RpcOutcome} 类注释）。</p>
     */
    private RpcOutcome parseOutcome(String response) {
        if (response == null || response.isBlank()) {
            return new RpcOutcome.BizRejected(null, "collect-pay 返回空响应");
        }
        JSONObject body = JSON.parseObject(response);
        if (body == null) {
            return new RpcOutcome.BizRejected(null, "collect-pay 返回无法解析的响应");
        }
        return RpcOutcome.ofRetCode(body.getString("retCode"), body.getString("retMsg"));
    }

}
