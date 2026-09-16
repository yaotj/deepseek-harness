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
 *
 * <p>三个方法都返回结果对象而不是 {@code null} / {@code false}：调用方 MUST 按
 * {@link CardPoolOutcome} 分流，把「池空」「参数或票种被拒」「远端不可达」区别对待，
 * NEVER 再统一报成「无可分配逻辑卡号」——那会把配置错误和网络故障都误报成卡池耗尽。</p>
 */
@Service
public class CardPoolClient extends ProxyWebClient {

    private static final Logger log = LoggerFactory.getLogger(CardPoolClient.class);

    private static final String RESERVATIONS_PATH = "/internal/card-pools/reservations";

    private static final String MAINTENANCE_PATH = "/internal/card-pools/maintenance";

    /**
     * 构造逻辑卡号池客户端，经 ProxyWebClient 以 HTTP 直连 card-pool-server（默认端口 9111）。
     *
     * @param baseUrl          card-pool-server 基础地址，取自配置 {@code service.cardPool.url}
     * @param openLogger       是否打印请求 / 响应报文日志，取自配置 {@code service.cardPool.openLogger}
     * @param webClientBuilder Spring 注入的 WebClient 构造器，由父类完成实际客户端装配
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
     *
     * <p>同 businessType + businessId 重复调用是幂等的：卡号处于 RESERVED 或 ASSIGNED 时都返回同一张卡号，
     * 因此上游重试或重复请求不会额外消耗号段。</p>
     *
     * @param request 预占请求，含票种码、业务类型、业务流水号与归属方
     * @return 预占结果；SUCCESS 时 data 非空，POOL_EMPTY 表示该票种池空，REJECTED 表示票种或归属不合法，
     *         CALL_FAILED 表示远端不可达或响应无法解析
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
     *
     * <p>服务端已做幂等兜底，重复确认同一 reservationId 仍返回成功。</p>
     *
     * @param reservationId 预占记录标识，取 {@link #reserve} 返回数据的 reservationId
     * @param businessId    业务流水号，须与预占时一致，供服务端校验归属
     * @return 确认结果；REJECTED 表示归属或状态不允许（服务端给出了明确否定），CALL_FAILED 表示没问到结论、可重试
     */
    public CardPoolActionResult confirm(String reservationId, String businessId) {
        return action(RESERVATIONS_PATH + "/" + reservationId + "/confirm", businessId, "确认");
    }

    /**
     * 释放预占，把该逻辑卡号回退为 AVAILABLE，用于开户 / 激活失败后归还卡号。
     *
     * <p>服务端已做幂等兜底，重复释放同一 reservationId 仍返回成功；已确认（ASSIGNED）的卡号不允许释放，
     * 会返回 REJECTED。CALL_FAILED 时不必强行重试到成功，预占超时回收会兜底。</p>
     *
     * @param reservationId 预占记录标识，取 {@link #reserve} 返回数据的 reservationId
     * @param businessId    业务流水号，须与预占时一致，供服务端校验归属
     * @return 释放结果
     */
    public CardPoolActionResult release(String reservationId, String businessId) {
        return action(RESERVATIONS_PATH + "/" + reservationId + "/release", businessId, "释放");
    }

    /**
     * 触发一轮卡池维护（回收超时预占 + 按阈值补货 + 推进批次），调用
     * {@code POST /internal/card-pools/maintenance}。
     *
     * <p>服务端只做**受理**：提交给单线程维护池后立即返回，ACC 申请 / FTP 下载 / 十万行入库都在
     * 服务端后台跑完，因此本方法返回成功**不代表这一轮已经导完**，执行结果 MUST 看 card-pool-server
     * 日志与 {@code /card-pools/summary}。</p>
     *
     * <p>{@code data.accepted=false} 表示上一轮尚未结束，本轮被丢弃。这是**正常的限流行为**，
     * 不是失败：导入十万行远超 5 分钟的调度间隔时会常态出现。调用方 NEVER 因此抛异常告警，
     * 否则前台调度日志会长期一片红。</p>
     *
     * <p>headers 由调用方组装 W3C {@code traceparent} 与 {@code X-Vlogs-Capture}，口径同
     * {@code ParaClient.quartzScanFtpPara}：{@code ProxyWebClient} 不会自动注入 trace 头，
     * **NEVER 传自定义 {@code traceId} 头**（下游 {@code FirstFilter} 会把头名小写后塞 MDC，
     * 与 log4j2 的 {@code %X{traceId}} 大小写对不上，等于白传）。</p>
     *
     * @param headers 需要透传的请求头，可传空 Map
     * @return SUCCESS 表示服务端已受理或已明确回绝本轮（message 带原文）；
     *         CALL_FAILED 表示远端不可达、无响应或响应无法解析
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
     *
     * @param path       目标路径
     * @param businessId 业务流水号
     * @param action     动作名，仅用于失败消息
     * @return 动作结果
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
