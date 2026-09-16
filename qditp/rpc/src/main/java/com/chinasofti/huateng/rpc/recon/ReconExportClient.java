package com.chinasofti.huateng.rpc.recon;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.recon.ReconExportReqDTO;
import com.chinasofti.huateng.model.recon.ReconExportRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * recon-server 向各源服务下发抽取指令的客户端。
 *
 * <p>与其他 Client 不同，本类**不绑定单一目标地址**：三个源服务地址各不相同，调用时传入完整
 * URL。实现方式复用 {@code rpc} 模块既有的「动态路由」机制——即
 * {@link com.chinasofti.huateng.rpc.route.URLDynamicRouter} 的做法：**把 baseUrl 传空串**，
 * 每次调用自己给出绝对 URL。</p>
 *
 * <p><b>baseUrl MUST 保持空串，NEVER 填 {@code service.recon.self-url} 之类的占位地址。</b>
 * 原因有两层：</p>
 * <ol>
 *   <li>{@code InternalMicroHttp.logRequest()} 打的 URL 是无条件的
 *       {@code joinUrl(getBaseUrl(), url)}，占位 baseUrl 会让 INFO 日志出现
 *       {@code url=http://127.0.0.1:9112/http://gate-txn-pay-server-...:30019/internal/recon/export}
 *       这种双份地址（2026-09-11 端到端实跑实录），把人往「打到自己身上」的方向误导；</li>
 *   <li>真正发请求时能否忽略 baseUrl，取决于 {@code DefaultUriBuilderFactory} 的实现细节
 *       （绝对 URL 带 host 时才丢弃 baseUri）。依赖这个细节属于**隐式契约**，
 *       baseUrl 留空则不论框架实现如何都只有绝对 URL 一个来源。</li>
 * </ol>
 *
 * <p>返回 {@code accepted=true} 只代表源服务**受理**，抽取仍在对端后台跑。收齐判定 MUST 以
 * {@code RECON_BATCH_SOURCE.STATUS} 为准，NEVER 把受理当成完成。</p>
 *
 * <p><b>【开发测试阶段：不再发送 {@code X-Recon-Token}】</b>用户 2026-09-11 要求
 * 「删除令牌要求，不用令牌了，当前处于开发测试阶段」，三个源服务的
 * {@code /internal/recon/export} 已不校验该头，故这里连带去掉发送。恢复鉴权时两端 MUST 同时改回。</p>
 *
 * <p><b>下发时固定带 {@code X-Vlogs-Capture: 1}（2026-09-14 新增，NEVER 删）。</b>
 * 三个源服务的 INFO 日志能不能进 VictoriaLogs，取决于它们 MDC 里有没有 {@code x-vlogs-capture=1}
 * ——公共 {@code log4j2-linux.xml} 的 VictoriaLogs appender 只对命中该键的链路全量上报，
 * 其余只上报 WARN 及以上。而 {@code ProxyWebClient.addAuthorizationToHeader} **只转发
 * {@code authorization} 一个头**，web-admin 的 {@code QuartzTraceUtils.traceHeaders} 发出的
 * {@code X-Vlogs-Capture} 到 recon-server 这一跳就断了。少了本行，前台调度日志按 traceId 反查
 * 只能看到 web-admin + recon-server 两段，三个源的 INFO **一条都查不到**（2026-09-14 实测）。</p>
 *
 * <p>这里写死 {@code 1} 而不是「有则透传」，是因为本端点**只被日终对账下发调用、每天每源一次**，
 * 量级可忽略，写死能让人工 curl 复现问题时也拿到完整链路。
 * <b>NEVER 把这个写法照搬到高频业务端点</b>——那等于把该链路的全部 INFO 灌进日志库。</p>
 */
@Service
public class ReconExportClient extends ProxyWebClient {

    private static final Logger log = LoggerFactory.getLogger(ReconExportClient.class);

    private static final String EXPORT_PATH = "/internal/recon/export";

    /**
     * 让源服务侧的 INFO 日志进 VictoriaLogs 的开关头，取值固定 {@code 1}。
     *
     * <p>大小写无关：{@code FirstFilter} 会把所有请求头**小写**后塞进 MDC，
     * 与 appender 里 {@code <KeyValuePair key="x-vlogs-capture" value="1" />} 对得上。</p>
     */
    private static final String VLOGS_CAPTURE_HEADER = "X-Vlogs-Capture";

    /**
     * 构造抽取指令下发客户端。
     *
     * @param openLogger       是否打印报文日志，指令报文很小，默认开启
     * @param webClientBuilder Spring 注入的 WebClient 构造器
     */
    public ReconExportClient(@Value("${service.recon.openLogger:true}") boolean openLogger,
                             WebClient.Builder webClientBuilder) {
        super("", openLogger, webClientBuilder);
    }

    /**
     * 源服务收到指令后要现场起 Oracle 抽取，握手与首次响应可能被慢 SQL 拖住，
     * 因此响应超时给到分钟级；默认 10 秒与原先的 30 秒都会在真实环境稳定超时，
     * 超时即被 {@link #dispatch} 判成「调用源服务异常」，把来源错误地标成 FAILED。
     */
    @Override
    protected Duration getResponseTimeout() {
        return Duration.ofMinutes(5);
    }

    /**
     * 向一个源服务下发抽取指令。
     *
     * @param sourceBaseUrl 源服务基础地址，MUST 是绝对地址，如
     *                      {@code http://gate-txn-pay-server-jomf4-svc.itp.svc:30019}
     * @param request       抽取指令
     * @return 受理结果；地址非法、远端不可达或响应无法解析时返回 {@code accepted=false} 并带原因，
     *         **本方法从不抛异常**——调用方 {@code ReconOrchestrationService.dispatchSource} 依赖这个语义
     *         把来源逐个标成 FAILED 后继续下发其余源，抛异常会让同一轮里剩下的源全部漏发
     */
    public ReconExportRespDTO dispatch(String sourceBaseUrl, ReconExportReqDTO request) {
        String url;
        try {
            url = normalize(sourceBaseUrl) + EXPORT_PATH;
        } catch (RuntimeException ex) {
            log.error("对账抽取指令的源服务地址非法 sourceBaseUrl={}, batchId={}, msg={}",
                    sourceBaseUrl, request.getBatchId(), ex.getMessage());
            return ReconExportRespDTO.rejected("源服务地址非法: " + ex.getMessage());
        }
        String response;
        try {
            Map<String, String> headers = new HashMap<>(2);
            headers.put(VLOGS_CAPTURE_HEADER, "1");
            response = postJsonAndGetResponse(url, request, headers);
        } catch (RuntimeException ex) {
            log.error("下发对账抽取指令失败 url={}, batchId={}, msg={}", url, request.getBatchId(), ex.getMessage());
            return ReconExportRespDTO.rejected("调用源服务异常: " + ex.getMessage());
        }
        if (response == null || response.isBlank()) {
            return ReconExportRespDTO.rejected("源服务无响应");
        }
        try {
            ReconExportRespDTO parsed = JSON.parseObject(response, ReconExportRespDTO.class);
            return parsed == null ? ReconExportRespDTO.rejected("源服务响应为空对象: " + response) : parsed;
        } catch (RuntimeException ex) {
            return ReconExportRespDTO.rejected("源服务响应无法解析: " + response);
        }
    }

    /**
     * 归一化源服务基础地址：去掉尾部 {@code /} 并强制要求带 {@code http://} / {@code https://} 前缀。
     *
     * <p>baseUrl 已经是空串，绝对 URL 是唯一的地址来源，因此**缺 scheme 就必须当配置错误直接拒绝**，
     * NEVER 放过去让 WebClient 把它当相对路径拼到空 baseUrl 上——那会得到一个没有 host 的请求，
     * 报错信息与「源服务不可达」难以区分。</p>
     */
    private String normalize(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("源服务地址未配置");
        }
        String trimmed = baseUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("源服务地址必须是带 http:// 或 https:// 的绝对地址: " + trimmed);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
