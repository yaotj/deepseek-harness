package com.chinasofti.huateng.rpc.cardpool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.cardpool.CardPoolActionResult;
import com.chinasofti.huateng.model.cardpool.CardPoolOutcome;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationActionReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationRespDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReserveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * 逻辑卡号池预占协议的内部客户端。
 */
@Service
public class CardPoolClient extends ProxyWebClient {

    private static final Logger log = LoggerFactory.getLogger(CardPoolClient.class);

    private static final String RESERVATIONS_PATH = "/internal/card-pools/reservations";

    private static final String MAINTENANCE_PATH = "/internal/card-pools/maintenance";

    /**
     * 构造逻辑卡号池客户端，经 ProxyWebClient 以 HTTP 直连 card-pool-server（默认端口 9111）。
     * @param baseUrl card-pool-server 基础地址，取自配置 {@code service.cardPool.url}
     * @param openLogger 是否打印请求 / 响应报文日志，取自配置 {@code service.cardPool.openLogger}
     * @param webClientBuilder Spring 注入的 WebClient 构造器，由父类完成实际客户端装配。
     */
    public CardPoolClient(@Value("${service.cardPool.url:card-pool-service}") String baseUrl,
                          @Value("${service.cardPool.openLogger:true}") boolean openLogger,
                          WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    @Override
    protected Duration getResponseTimeout() {
        return Duration.ofSeconds(15);
    }

    /**
     * 预占一个指定票种的逻辑卡号，调用 {@code POST /internal/card-pools/reservations}。
     * @param request 预占请求，含票种码、业务类型、业务流水号与归。
     * @return 预占结果；SUCCESS 时 data 非空，POOL_EMPTY 表示该票种池空，REJECTED 表示票种或归。
     */
    public CardPoolReserveResult reserve(CardPoolReservationReqDTO request) {
        String response;
        try {
            response = postJsonAndGetResponse(RESERVATIONS_PATH, request);
        } catch (RuntimeException ex) {
            return CardPoolReserveResult.failure(CardPoolOutcome.CALL_FAILED, "调用卡池预占异常: " + ex.getMessage());
        }
        if (response == null || response.isBlank()) {
            return CardPoolReserveResult.failure(CardPoolOutcome.CALL_FAILED, "卡池预占无响应");
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(response);
        } catch (RuntimeException ex) {
            return CardPoolReserveResult.failure(CardPoolOutcome.CALL_FAILED, "卡池预占响应无法解析: " + response);
        }
        String code = json.getStr("code");
        String message = json.getStr("msg");
        if ("400".equals(code)) {
            return CardPoolReserveResult.failure(CardPoolOutcome.REJECTED, message);
        }
        if (!"200".equals(code)) {
            return CardPoolReserveResult.failure(CardPoolOutcome.CALL_FAILED, "卡池预占返回 code=" + code + ", msg=" + message);
        }
        Object data = json.get("data");
        if (data == null) {
            return CardPoolReserveResult.failure(CardPoolOutcome.POOL_EMPTY, "该票种卡池已空");
        }
        return CardPoolReserveResult.success(JSONUtil.toBean(data.toString(), CardPoolReservationRespDTO.class));
    }

    /**
     * 确认预占，把该逻辑卡号置为 ASSIGNED，用于开户 / 激活成功后收口。
     * @param reservationId 预占记录标识，取 {@link #reserve} 返回数据的 reservationId。
     * @param businessId 业务流水号，须与预占时一致，供服务端校验归。
     * @return 确认结果；REJECTED 表示归。
     */
    public CardPoolActionResult confirm(String reservationId, String businessId) {
        return action(RESERVATIONS_PATH + "/" + reservationId + "/confirm", businessId, "确认");
    }

    /**
     * 释放预占，把该逻辑卡号回退为 AVAILABLE，用于开户 / 激活失败后归还卡号。
     * @param reservationId 预占记录标识，取 {@link #reserve} 返回数据的 reservationId。
     * @param businessId 业务流水号，须与预占时一致，供服务端校验归。
     * @return 释放结果。
     */
    public CardPoolActionResult release(String reservationId, String businessId) {
        return action(RESERVATIONS_PATH + "/" + reservationId + "/release", businessId, "释放");
    }

    /**
     * 触发一轮卡池维护（回收超时预占 + 按阈值补货 + 推进批次），调用。
     * @param headers 需要透传的请求头，可传空 Map。
     * @return SUCCESS 表示服务端已受理或已明确回绝本轮（message 带原文）
     */
    public CardPoolActionResult runMaintenance(Map<String, String> headers) {
        String response;
        try {
            response = postJsonAndGetResponse(MAINTENANCE_PATH, Collections.emptyMap(),
                    headers == null ? Collections.emptyMap() : headers);
        } catch (RuntimeException ex) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED, "调用卡池维护异常: " + ex.getMessage());
        }
        if (response == null || response.isBlank()) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED, "卡池维护无响应");
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(response);
        } catch (RuntimeException ex) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED, "卡池维护响应无法解析: " + response);
        }
        String code = json.getStr("code");
        if (!"200".equals(code)) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED,
                    "卡池维护返回 code=" + code + ", msg=" + json.getStr("msg"));
        }
        JSONObject data = json.getJSONObject("data");
        Boolean accepted = data == null ? null : data.getBool("accepted");
        String message = data == null ? null : data.getStr("message");
        log.info("调用card-pool-server卡池维护接口返回 path={}, accepted={}, message={}",
                MAINTENANCE_PATH, accepted, message);
        return CardPoolActionResult.success("accepted=" + accepted + ", message=" + message);
    }

    /**
     * 发起确认 / 释放并把响应翻译成结果对象。
     * @param path 目标路径。
     * @param businessId 业务流水号。
     * @param action 动作名，仅用于失败消息。
     * @return 动作结果。
     */
    private CardPoolActionResult action(String path, String businessId, String action) {
        CardPoolReservationActionReqDTO request = new CardPoolReservationActionReqDTO();
        request.setBusinessId(businessId);
        String response;
        try {
            response = postJsonAndGetResponse(path, request);
        } catch (RuntimeException ex) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED,
                    "调用卡池" + action + "异常: " + ex.getMessage());
        }
        if (response == null || response.isBlank()) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED, "卡池" + action + "无响应");
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(response);
        } catch (RuntimeException ex) {
            return CardPoolActionResult.failure(CardPoolOutcome.CALL_FAILED,
                    "卡池" + action + "响应无法解析: " + response);
        }
        if ("200".equals(json.getStr("code"))) {
            return CardPoolActionResult.success();
        }
        return CardPoolActionResult.failure(CardPoolOutcome.REJECTED, json.getStr("msg"));
    }
}
