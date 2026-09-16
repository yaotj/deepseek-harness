package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.gatetxnpay.entity.MetroTransferPushTask;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公交换乘行程推送适配器。调用异步化，失败不影响地铁扣费订单。
 *
 * <p>对接的是**外部公交卡系统**（不是 ITP 内部服务），因此按 {@code pay-sign-server} 的
 * {@code PayGatewayClient} 的定位留在业务模块里，<b>NEVER 搬进 {@code rpc} 模块</b>。
 * 2026-09-14 从裸 {@code WebClient.Builder#build()} 改为继承 {@link ProxyWebClient}，
 * 拿到连接池、3s 连接超时、出入报文日志开关与统一失败日志；三态返回语义一行未改。</p>
 */
@Service
public class MetroTransferPushClient extends ProxyWebClient {
    private static final Logger log = LoggerFactory.getLogger(MetroTransferPushClient.class);
    private final String url;
    private final boolean enabled;

    public MetroTransferPushClient(
            @Value("${wallet.metro-transfer-url:http://172.20.202.10:8885/buscard/busApi/2App/v1/pushMetroTran}") String url,
            @Value("${wallet.metro-transfer-enabled:false}") boolean enabled,
            @Value("${wallet.metro-transfer-open-logger:false}") boolean openLogger,
            @Value("${wallet.metro-transfer-timeout-ms:3000}") long timeoutMs,
            WebClient.Builder webClientBuilder) {
        super(url, openLogger, webClientBuilder, Duration.ofMillis(timeoutMs));
        this.url = url;
        this.enabled = enabled;
    }

    /**
     * 推送一笔换乘行程，返回三态结果而**不是** void + 抛异常（AGENTS.md §5.2 / ADR-D45）。
     *
     * <p>改造前本方法对三种情况一律抛 {@code IllegalStateException}：网络不可达、超时、
     * 以及**对端明确答复非 {@code 0000} 的业务拒绝**。调用方只能笼统 catch，于是「这笔数据对端不收」
     * 被当成网络抖动退避重推满 10 次才转 {@code FAILED}——白等约 1 小时，且期间对端被反复打。
     * 现在业务拒绝走 {@link RpcOutcome.BizRejected}，调用方 MUST 一次即终态。</p>
     *
     * <p>{@code enabled=false} 仍抛异常：调用方 {@code MetroTransferPushTaskProcessor} 在进入循环前
     * 已判过同一个开关，走到这里说明有新调用方漏判，属编程错误，**NEVER 悄悄返回某个 outcome**
     * ——那会把一批任务按「投递结论」落库，而实际上一个请求都没发出去。</p>
     *
     * <p>{@link ProxyWebClient#postFormAndGetResponse} 把连接失败、超时与 4xx/5xx 一律包成
     * {@code RuntimeException} 抛出，因此下面那个 catch 覆盖的仍是「没拿到对端业务应答」这一类，
     * 与改造前 {@code WebClient} 裸调的分类一致，**NEVER 把它收窄成只 catch 某个具体异常**。</p>
     *
     * <p><b>MUST 用 {@code application/x-www-form-urlencoded}，NEVER 改回 JSON body。</b>
     * 对端 {@code com.bestone.buscard} 用 {@code RequestHandlerVO} 接参（按表单字段绑定），
     * 发 JSON body 时它那六个字段**全部为 {@code null}**、随后在取 {@code bizData} 处 NPE，
     * 对外表现是 <b>HTTP 200 + {@code {"retCode":"1002","retMsg":"接收地铁交易数据失败null"}}</b>
     * ——注意 {@code retMsg} 尾部那个字面量 {@code null} 就是对端拼了个 null 的 {@code e.getMessage()}，
     * 是「参数一个都没绑上」的指纹，**NEVER 把它当成我方数据内容有问题去改 bizData 字段**。
     * 2026-09-15 用九种报文形态 + 四组数据实测：只要是 JSON body，回的字节完全一样；
     * 拿到对端日志 {@code RequestHandlerVO@61810503[providerId=<null>,...,bizData=<null>]} 才定性。
     * 这也与 AGENTS.md §4「请求格式 application/x-www-form-urlencoded」一致。</p>
     *
     * <p><b>bizData 只有五个字段，NEVER 往里加 {@code cardType}。</b>
     * 2026-09-15 单变量对照实测（同一地址、同为 form-urlencoded、只动 bizData 的键）：
     * 带 {@code cardType} → <b>1002</b>；键名换成 {@code reserve} → <b>0000</b>；
     * <b>{@code cardType} 与 {@code reserve} 同时给 → 又变回 1002</b>；**整个键都不给 → 0000**。
     * 第三条说明对端不是「多给一个字段无所谓」，而是**见到 {@code cardType} 这个键就炸**
     * （大概率它 bizData 的 DTO 上那个同名字段类型对不上或有严格校验）；
     * 第四条说明这个位置对端根本不需要，因此按接口方要求**直接不发**，
     * **NEVER 为了「留个位置」塞 {@code reserve} 空串或卡类型** —— 那只是徒增一个对端不读的字段。
     * 卡类型在我方 {@code METRO_TRANSFER_PUSH_TASK.CARD_TYPE} 已有留痕，不依赖出向报文。</p>
     *
     * <p>连带一条排查经验：**Content-Type 与 bizData 键名这两个缺陷是叠加的，单改任何一个都仍然是 1002** ——
     * JSON body + 去掉 {@code cardType} 是 1002，form-urlencoded + 带 {@code cardType} 也是 1002。
     * 于是「换一个变量试一次、没好就否掉这个方向」的排查法在这里必然误判，
     * 前后共试了十几种形态都回同样的字节就是这个原因。</p>
     */
    public RpcOutcome push(MetroTransferPushTask task) {
        if (!enabled) {
            throw new IllegalStateException("公交换乘推送未启用");
        }
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("thirdUserId", task.getThirdUserId());
        bizData.put("transDate", task.getTransDate());
        bizData.put("transTime", task.getTransTime());
        bizData.put("payChannelType", task.getPayChannelType());
        bizData.put("transferFlag", task.getTransferFlag());
        Map<String, String> request = new LinkedHashMap<>();
        request.put("providerId", "01");
        request.put("charset", "utf-8");
        request.put("format", "json");
        request.put("timestamp", String.valueOf(System.currentTimeMillis()));
        request.put("signType", "00");
        request.put("bizData", JSON.toJSONString(bizData));
        String response;
        try {
            response = postFormAndGetResponse(url, request, null);
        } catch (RuntimeException e) {
            return new RpcOutcome.Unreachable(e);
        }
        String retCode;
        try {
            retCode = response == null ? null : JSON.parseObject(response).getString("retCode");
        } catch (RuntimeException e) {
            return new RpcOutcome.BizRejected("PARSE_ERROR", "对端响应无法解析：" + response);
        }
        RpcOutcome outcome = RpcOutcome.ofRetCode(retCode, response);
        if (outcome.isOk()) {
            log.info("公交换乘行程推送完成, orderNo={}, response={}", task.getOrderNo(), response);
        }
        return outcome;
    }
}
