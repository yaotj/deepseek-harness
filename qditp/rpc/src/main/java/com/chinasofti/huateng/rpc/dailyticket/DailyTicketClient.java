package com.chinasofti.huateng.rpc.dailyticket;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketActivateReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketFreeOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketFreeOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayQueryResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketSyncOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoResult;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderResult;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * daily-ticket-server RPC客户端。
 */
@Service
public class DailyTicketClient extends ProxyWebClient {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketClient.class);

    public DailyTicketClient(@Value("${service.dailyTicket.url:daily-ticket-service}") String baseUrl,
                             @Value("${service.dailyTicket.openLogger:true}") boolean openLogger,
                             WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * 调用日票下单接口。
     */
    public DailyTicketOrderResult requestCountingOrder(@RequestBody DailyTicketOrderReqDTO request) {
        String path = "/ci/daily-ticket/requestCountingOrder";
        log.info("调用daily-ticket-server日票下单接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server日票下单接口原始返回 path={}, response={}", path, result);
        DailyTicketOrderResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketOrderResult>() {
        }, true);
        log.info("调用daily-ticket-server日票下单接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用旅游票下单接口（IF8A-70）。
     */
    public TravelTicketOrderResult requestTravelOrder(@RequestBody TravelTicketOrderReqDTO request) {
        String path = "/ci/daily-ticket/requestTravelOrder";
        log.info("调用daily-ticket-server旅游票下单接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server旅游票下单接口原始返回 path={}, response={}", path, result);
        TravelTicketOrderResult response = JSONUtil.toBean(result, new TypeReference<TravelTicketOrderResult>() {
        }, true);
        log.info("调用daily-ticket-server旅游票下单接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用免费票下单接口（IF8A-73）。
     */
    public DailyTicketFreeOrderResult requestOrderFree(@RequestBody DailyTicketFreeOrderReqDTO request) {
        String path = "/ci/daily-ticket/requestOrderFree";
        log.info("调用daily-ticket-server免费票下单接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server免费票下单接口原始返回 path={}, response={}", path, result);
        DailyTicketFreeOrderResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketFreeOrderResult>() {
        }, true);
        log.info("调用daily-ticket-server免费票下单接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用小程序订单状态同步接口（IF8A-72）。
     */
    public DailyTicketBaseResult syncOrder(@RequestBody DailyTicketSyncOrderReqDTO request) {
        String path = "/ci/daily-ticket/syncOrder";
        log.info("调用daily-ticket-server小程序订单状态同步接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server小程序订单状态同步接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server小程序订单状态同步接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用日票支付接口。
     */
    public DailyTicketPayResult requestPay(@RequestBody DailyTicketPayReqDTO request) {
        String path = "/ci/daily-ticket/payment/requestPay";
        log.info("调用daily-ticket-server日票支付接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server日票支付接口原始返回 path={}, response={}", path, result);
        DailyTicketPayResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketPayResult>() {
        }, true);
        log.info("调用daily-ticket-server日票支付接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用日票支付结果查询接口。
     */
    public DailyTicketPayQueryResult requestPayResult(@RequestBody DailyTicketOrderNoReqDTO request) {
        String path = "/ci/daily-ticket/payment/requestPayResult";
        log.info("调用daily-ticket-server日票支付结果查询接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server日票支付结果查询接口原始返回 path={}, response={}", path, result);
        DailyTicketPayQueryResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketPayQueryResult>() {
        }, true);
        log.info("调用daily-ticket-server日票支付结果查询接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用日票退款接口。
     */
    public DailyTicketRefundResult requestRefundTicket(@RequestBody DailyTicketOrderNoReqDTO request) {
        String path = "/ci/daily-ticket/payment/requestRefundTicket";
        log.info("调用daily-ticket-server日票退款接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server日票退款接口原始返回 path={}, response={}", path, result);
        DailyTicketRefundResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketRefundResult>() {
        }, true);
        log.info("调用daily-ticket-server日票退款接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用日票取消订单接口。
     */
    public DailyTicketBaseResult cancelOrder(@RequestBody DailyTicketOrderNoReqDTO request) {
        String path = "/ci/daily-ticket/ticket/cancelOrder";
        log.info("调用daily-ticket-server日票取消订单接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server日票取消订单接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server日票取消订单接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用日票激活接口。
     */
    public DailyTicketBaseResult updateTicket(@RequestBody DailyTicketActivateReqDTO request) {
        String path = "/ci/daily-ticket/ticket/updateTicket";
        log.info("调用daily-ticket-server日票激活接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server日票激活接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server日票激活接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用通知ACC车票已使用接口。
     */
    public DailyTicketBaseResult updateAndNotice(@RequestBody DailyTicketUsedNoticeReqDTO request) {
        String path = "/ci/daily-ticket/ticket/updateAndNotice";
        log.info("调用daily-ticket-server通知ACC车票已使用接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server通知ACC车票已使用接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server通知ACC车票已使用接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 转发支付结果回调到日票服务。
     */
    public DailyTicketBaseResult receivePayResult(@RequestBody DailyTicketPayCallbackReqDTO request) {
        String path = "/ci/daily-ticket/payment/receivePayResult";
        log.info("调用daily-ticket-server支付结果回调接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server支付结果回调接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server支付结果回调接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用日票支付结果通知 APP 的补偿接口。
     *
     * <p>该接口由 web-admin 的 Quartz 任务通过 RPC 调用，daily-ticket-server
     * 内部扫描待重试的 IF8B-05 通知任务并负责实际请求 APP。</p>
     */
    public DailyTicketBaseResult compensatePayNotify(Map<String, String> headers) {
        String path = "/internal/daily-ticket/pay/notify";
        log.info("调用daily-ticket-server支付结果通知补偿接口 path={}, headers={}", path, headers);
        String result = postJsonAndGetResponse(path, new java.util.HashMap<>(), headers);
        log.info("调用daily-ticket-server支付结果通知补偿接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server支付结果通知补偿接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用日票激活后通知 ACC 发售的补偿接口。
     *
     * <p>该接口由 web-admin 的 Quartz 任务通过 RPC 调用，daily-ticket-server
     * 内部扫描待重试的 ACC 发售通知任务并负责实际请求 ACC。</p>
     */
    public DailyTicketBaseResult compensateAccActiveNotify(Map<String, String> headers) {
        String path = "/internal/daily-ticket/acc/active-notify";
        log.info("调用daily-ticket-server ACC发售通知补偿接口 path={}, headers={}", path, headers);
        String result = postJsonAndGetResponse(path, new java.util.HashMap<>(), headers);
        log.info("调用daily-ticket-server ACC发售通知补偿接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server ACC发售通知补偿接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用多日票批量退款（当日）内部端点（甲方需求 16）。
     *
     * <p>由 web-admin 的 Quartz 任务通过 RPC 调用，daily-ticket-server 内部扫「已支付未激活且过等待期」
     * 的日票 / 旅游票主单并逐笔发起退款。{@code retCode=9998} 表示上一轮仍在执行，属限流不是失败。</p>
     */
    public DailyTicketBaseResult batchRefundDaily(Map<String, String> headers) {
        String path = "/internal/daily-ticket/batch-refund/daily";
        log.info("调用daily-ticket-server多日票批量退款(当日)接口 path={}, headers={}", path, headers);
        String result = postJsonAndGetResponse(path, new java.util.HashMap<>(), headers);
        log.info("调用daily-ticket-server多日票批量退款(当日)接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server多日票批量退款(当日)接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用多日票批量退款（月度）内部端点（甲方需求 17）。
     *
     * <p>与当日那条只差回溯窗口（默认 60 天 vs 7 天），谓词有重叠、靠退款单唯一索引幂等兼容。</p>
     */
    public DailyTicketBaseResult batchRefundMonthly(Map<String, String> headers) {
        String path = "/internal/daily-ticket/batch-refund/monthly";
        log.info("调用daily-ticket-server多日票批量退款(月度)接口 path={}, headers={}", path, headers);
        String result = postJsonAndGetResponse(path, new java.util.HashMap<>(), headers);
        log.info("调用daily-ticket-server多日票批量退款(月度)接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server多日票批量退款(月度)接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 调用日票有效期过期收敛内部端点。
     *
     * <p>由 web-admin 的 Quartz 任务通过 RPC 调用，daily-ticket-server 内部把「有效期已过、
     * 状态还停在 ACTIVATED / USED」的票逐条 CAS 推进成 EXPIRED。
     * {@code retCode=9998} 表示上一轮仍在执行，属限流不是失败。</p>
     */
    public DailyTicketBaseResult convergeExpiredTickets(Map<String, String> headers) {
        String path = "/internal/daily-ticket/expire/converge";
        log.info("调用daily-ticket-server日票过期收敛接口 path={}, headers={}", path, headers);
        String result = postJsonAndGetResponse(path, new java.util.HashMap<>(), headers);
        log.info("调用daily-ticket-server日票过期收敛接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server日票过期收敛接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 转发退款结果回调到日票服务（支付中心网关 §5.2）。
     */
    public DailyTicketBaseResult receiveRefundResult(@RequestBody DailyTicketRefundCallbackReqDTO request) {
        String path = "/ci/daily-ticket/payment/receiveRefundResult";
        log.info("调用daily-ticket-server退款结果回调接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server退款结果回调接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server退款结果回调接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 查询日票票实例信息（ticketCode、actualTimes）。
     */
    public QueryDailyTicketInfoResult queryDailyTicketInfo(QueryDailyTicketInfoReqDTO request) {
        String path = "/ci/daily-ticket/queryDailyTicketInfo";
        log.info("调用daily-ticket-server查询日票信息接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server查询日票信息接口原始返回 path={}, response={}", path, result);
        QueryDailyTicketInfoResult response = JSONUtil.toBean(result, new TypeReference<QueryDailyTicketInfoResult>() {
        }, true);
        log.info("调用daily-ticket-server查询日票信息接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 按票号查日票购票支付信息（payTradeOrderNo、payOrderNoDate、payChannelCode）。
     */
    public QueryDailyTicketPayInfoResult queryDailyTicketPayInfo(QueryDailyTicketPayInfoReqDTO request) {
        String path = "/ci/daily-ticket/queryDailyTicketPayInfo";
        log.info("调用daily-ticket-server查询日票支付信息接口入参 path={}, request={}", path, JSON.toJSONString(request));
        String result = postJsonAndGetResponse(path, request);
        log.info("调用daily-ticket-server查询日票支付信息接口原始返回 path={}, response={}", path, result);
        QueryDailyTicketPayInfoResult response = JSONUtil.toBean(result, new TypeReference<QueryDailyTicketPayInfoResult>() {
        }, true);
        log.info("调用daily-ticket-server查询日票支付信息接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 日票进站校验（有效期、未完成出站、计次票次数检查）。
     */
    public DailyTicketBaseResult entryCheck(String cardNum) {
        String path = "/ci/daily-ticket/entry/check";
        Map<String, String> body = new java.util.HashMap<>();
        body.put("cardNum", cardNum);
        log.info("调用daily-ticket-server日票进站校验接口入参 path={}, cardNum={}", path, cardNum);
        String result = postJsonAndGetResponse(path, body);
        log.info("调用daily-ticket-server日票进站校验接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server日票进站校验接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 日票乘车可用性查询（拉码 IF8A-03 前置，只读，不推进任何状态）。
     *
     * <p>返回 {@code Ok} 可发码；{@code BizRejected} 即对端明确判定不可用、调用方 MUST 拒发；
     * {@code Unreachable} 由调用方降级放行 —— 闸机侧还有一道权威校验。
     * <b>本方法绝不抛异常</b>，任何异常都归到 {@code Unreachable}。
     */
    public RpcOutcome checkRideAvailability(String cardNum) {
        String path = "/ci/daily-ticket/ticket/rideAvailability";
        try {
            Map<String, String> body = new java.util.HashMap<>();
            body.put("cardNum", cardNum);
            log.info("调用daily-ticket-server日票可用性校验接口入参 path={}, cardNum={}", path, cardNum);
            String result = postJsonAndGetResponse(path, body);
            log.info("调用daily-ticket-server日票可用性校验接口原始返回 path={}, response={}", path, result);
            DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
            }, true);
            log.info("调用daily-ticket-server日票可用性校验接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
            if (response == null) {
                return new RpcOutcome.Unreachable(new IllegalStateException("daily-ticket-server 返回为空"));
            }
            return RpcOutcome.ofRetCode(response.getRetCode(), response.getRetMsg());
        } catch (Exception e) {
            return new RpcOutcome.Unreachable(e);
        }
    }

    /**
     * 日票出站处理（扣减计次票次数，标记已使用）。
     */
    public DailyTicketBaseResult markUsed(String cardNum, Long countingEnd) {
        return markUsed(cardNum, countingEnd, null, null, null);
    }

    /**
     * 日票出站处理（扩展版，携带行程关联信息用于扣次明细记录）。
     */
    public DailyTicketBaseResult markUsed(String cardNum, Long countingEnd,
                                          String orderNo, String inStation, String outStation) {
        String path = "/ci/daily-ticket/ticket/markUsed";
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("cardNum", cardNum);
        body.put("countingEnd", countingEnd);
        body.put("orderNo", orderNo);
        body.put("inStation", inStation);
        body.put("outStation", outStation);
        log.info("调用daily-ticket-server日票出站处理接口入参 path={}, cardNum={}, countingEnd={}, orderNo={}",
                path, cardNum, countingEnd, orderNo);
        String result = postJsonAndGetResponse(path, body);
        log.info("调用daily-ticket-server日票出站处理接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server日票出站处理接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }

    /**
     * 查询日票扣次使用明细。
     */
    public DailyTicketBaseResult queryUsageLog(String cardNum) {
        String path = "/ci/daily-ticket/ticket/usageLog";
        Map<String, String> body = new java.util.HashMap<>();
        body.put("cardNum", cardNum);
        log.info("调用daily-ticket-server查询扣次明细接口入参 path={}, cardNum={}", path, cardNum);
        String result = postJsonAndGetResponse(path, body);
        log.info("调用daily-ticket-server查询扣次明细接口原始返回 path={}, response={}", path, result);
        DailyTicketBaseResult response = JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
        log.info("调用daily-ticket-server查询扣次明细接口解析返回 path={}, response={}", path, JSON.toJSONString(response));
        return response;
    }
}
