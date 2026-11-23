package org.qd.scg.dual;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import org.qd.scg.common.RouteConstants;
import org.qd.scg.event.RouteChangedEvent;
import org.qd.scg.filter.CachedBodyHttpServletRequest;
import org.qd.scg.model.RouteDef;
import org.qd.scg.service.GatewayRouteQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;


@Component
public class DualQueryService {

    private static final Logger log = LoggerFactory.getLogger(DualQueryService.class);

    static final String FALLBACK_BODY =
            "{\"orderNo\":null,\"retCode\":\"2003\",\"retMsg\":\"无激活的订单\"}";

    private final DualQueryProperties props;
    private final GatewayRouteQueryService routeQueryService;
    private final HttpClient httpClient;

    private final ExecutorService executor;

    private final SideCircuitBreaker newSideBreaker;
    private final SideCircuitBreaker legacySideBreaker;

    private volatile String newBaseUri;
    private volatile String legacyBaseUri;


    private volatile RewriteRule newRewrite;
    private volatile RewriteRule legacyRewrite;

    public DualQueryService(DualQueryProperties props, GatewayRouteQueryService routeQueryService) {
        this.props = props;
        this.routeQueryService = routeQueryService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "dual-query-side");
            t.setDaemon(true);
            return t;
        });
        this.newSideBreaker = new SideCircuitBreaker(props.getBreakerFailureThreshold(), props.getBreakerOpenMillis());
        this.legacySideBreaker = new SideCircuitBreaker(props.getBreakerFailureThreshold(), props.getBreakerOpenMillis());
    }

    @PostConstruct
    public void init() {
        refreshTargets();
    }

    @EventListener
    public void onRouteChanged(RouteChangedEvent event) {
        refreshTargets();
    }

    public void refreshTargets() {
        try {
            List<RouteDef> routes = routeQueryService.loadRoutes();
            for (RouteDef r : routes) {
                if (props.getNewRouteId().equals(r.id())) {
                    newBaseUri = stripTrailingSlash(r.uri());
                    newRewrite = parseRewrite(r.filters());
                } else if (props.getLegacyRouteId().equals(r.id())) {
                    legacyBaseUri = stripTrailingSlash(r.uri());
                    legacyRewrite = parseRewrite(r.filters());
                }
            }
            log.info("双发目标已刷新: new={}({}) legacy={}({})", newBaseUri, newRewrite, legacyBaseUri, legacyRewrite);
        } catch (Exception e) {
            log.warn("双发目标解析失败，沿用旧值: {}", e.getMessage());
        }
    }


    static RewriteRule parseRewrite(String filtersJson) {
        if (filtersJson == null || filtersJson.isBlank()) {
            return null;
        }
        int idx = filtersJson.indexOf("RewritePath=");
        if (idx < 0) {
            return null;
        }
        int start = idx + "RewritePath=".length();
        int comma = filtersJson.indexOf(',', start);
        if (comma < 0) {
            return null;
        }
        int end = filtersJson.indexOf('"', comma);
        if (end < 0) {
            end = filtersJson.length();
        }
        String regex = filtersJson.substring(start, comma).trim();
        String replacement = filtersJson.substring(comma + 1, end).trim();
        if (regex.isEmpty() || replacement.isEmpty()) {
            return null;
        }
        try {
            return new RewriteRule(Pattern.compile(regex), replacement);
        } catch (Exception e) {
            log.warn("RewritePath 正则无效，忽略改写: {}", regex, e);
            return null;
        }
    }

    String resolvePath(Side side, String path) {
        RewriteRule rule = side == Side.NEW ? newRewrite : legacyRewrite;
        if (rule == null || path == null) {
            return path;
        }
        String rewritten = rule.pattern().matcher(path).replaceAll(rule.replacement());
        if (!rewritten.equals(path)) {
            log.info("双发路径改写 side={} {} -> {}", side, path, rewritten);
        }
        return rewritten;
    }

    private static String stripTrailingSlash(String uri) {
        return uri != null && uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    public boolean matches(String path) {
        return props.isEnabled()
                && path != null && props.getPaths().contains(path)
                && newBaseUri != null && legacyBaseUri != null;
    }

    public ServerResponse handle(ServerRequest request) throws Exception {
        String path = request.path();
        ForwardRequest fwd = buildForward(request);

        boolean newAllowed = newSideBreaker.allowRequest();
        boolean legacyAllowed = legacySideBreaker.allowRequest();

        CompletableFuture<SideResult> newFuture = newAllowed
                ? CompletableFuture.supplyAsync(() -> fetchSide(Side.NEW, newBaseUri, fwd), executor)
                : CompletableFuture.completedFuture(SideResult.skipped(Side.NEW));
        CompletableFuture<SideResult> legacyFuture = legacyAllowed
                ? CompletableFuture.supplyAsync(() -> fetchSide(Side.LEGACY, legacyBaseUri, fwd), executor)
                : CompletableFuture.completedFuture(SideResult.skipped(Side.LEGACY));

        SideResult newRes = newFuture.join();
        SideResult legacyRes = legacyFuture.join();

        MergeOutcome outcome = merge(newRes, legacyRes);
        log.info("双发决策 path={} new={} legacy={} -> {}", path, newRes.summary(), legacyRes.summary(), outcome.winner());
        if (newRes.kind() == SideResult.Kind.POSITIVE && legacyRes.kind() == SideResult.Kind.POSITIVE) {
            log.warn("双发冲突: 新老 ITP 同时返回正向, path={} newOrder={} legacyOrder={}，已取新侧",
                    path, fastExtract(newRes.body(), "\"orderNo\""), fastExtract(legacyRes.body(), "\"orderNo\""));
        }
        if (newRes.kind() == SideResult.Kind.ERROR && legacyRes.kind() == SideResult.Kind.NEGATIVE) {
            log.warn("双发风险: 新 ITP 故障而老 ITP 负向, 可能掩盖新侧已激活订单, path={}", path);
        }
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(outcome.body());
    }

    static MergeOutcome merge(SideResult newRes, SideResult legacyRes) {
        boolean newPos = newRes.kind() == SideResult.Kind.POSITIVE;
        boolean legacyPos = legacyRes.kind() == SideResult.Kind.POSITIVE;
        if (newPos) {
            return new MergeOutcome("NEW", newRes.body());
        }
        if (legacyPos) {
            return new MergeOutcome("LEGACY", legacyRes.body());
        }
        if (newRes.kind() == SideResult.Kind.NEGATIVE) {
            return new MergeOutcome("NEW", newRes.body());
        }
        if (legacyRes.kind() == SideResult.Kind.NEGATIVE) {
            return new MergeOutcome("LEGACY", legacyRes.body());
        }
        return new MergeOutcome("FALLBACK", FALLBACK_BODY);
    }

    SideResult fetchSide(Side side, String baseUri, ForwardRequest fwd) {
        long start = System.currentTimeMillis();
        SideCircuitBreaker breaker = side == Side.NEW ? newSideBreaker : legacySideBreaker;
        try {
            HttpRequest httpRequest = buildHttpRequest(baseUri, fwd, resolvePath(side, fwd.path()));
            HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long elapsed = System.currentTimeMillis() - start;
            if (resp.statusCode() == 200) {
                breaker.onSuccess();
                return isPositive(resp.body())
                        ? SideResult.positive(side, resp.body(), elapsed)
                        : SideResult.negative(side, resp.body(), elapsed);
            }
            breaker.onFailure();
            return SideResult.error(side, "HTTP " + resp.statusCode(), elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            breaker.onFailure();
            log.warn("双发支路故障 side={} uri={} err={}", side, baseUri, e.getMessage());
            return SideResult.error(side, String.valueOf(e.getMessage()), elapsed);
        }
    }

    private HttpRequest buildHttpRequest(String baseUri, ForwardRequest fwd, String path) {
        StringBuilder url = new StringBuilder(baseUri).append(path);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofMillis(props.getTimeoutMs()));
        if (fwd.traceId() != null) {
            builder.header(RouteConstants.TRACE_ID_HEADER, fwd.traceId());
        }
        if (fwd.traceparent() != null && !fwd.traceparent().isBlank()) {
            builder.header(RouteConstants.TRACE_PARENT_HEADER, fwd.traceparent());
        }
        if (fwd.b3() != null && !fwd.b3().isBlank()) {
            builder.header(RouteConstants.B3_HEADER, fwd.b3());
        }
        if ("GET".equalsIgnoreCase(fwd.method())) {
            if (fwd.queryString() != null && !fwd.queryString().isBlank()) {
                builder.uri(URI.create(url.append('?').append(fwd.queryString()).toString()));
            }
            builder.GET();
        } else {
            builder.header("Content-Type",
                    fwd.contentType() != null ? fwd.contentType() : "application/x-www-form-urlencoded;charset=UTF-8");
            builder.POST(HttpRequest.BodyPublishers.ofString(
                    fwd.body() != null ? fwd.body() : "", StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    private ForwardRequest buildForward(ServerRequest request) throws Exception {
        HttpServletRequest sr = request.servletRequest();
        String body = null;
        if (sr instanceof CachedBodyHttpServletRequest cached) {
            byte[] cachedBody = cached.getCachedBody();
            if (cachedBody.length > 0) {
                body = new String(cachedBody, StandardCharsets.UTF_8);
            }
        } else if ("POST".equalsIgnoreCase(sr.getMethod())) {
            byte[] raw = sr.getInputStream().readAllBytes();
            if (raw.length > 0) {
                body = new String(raw, StandardCharsets.UTF_8);
            }
        }
        return new ForwardRequest(sr.getMethod(), sr.getRequestURI(), sr.getQueryString(),
                sr.getContentType(), body, sr.getHeader(RouteConstants.TRACE_ID_HEADER),
                sr.getHeader(RouteConstants.TRACE_PARENT_HEADER), sr.getHeader(RouteConstants.B3_HEADER));
    }

    static boolean isPositive(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        if (!"0000".equals(fastExtract(body, "\"retCode\""))) {
            return false;
        }
        String orderNo = fastExtract(body, "\"orderNo\"");
        return orderNo != null && !orderNo.isBlank();
    }

    static String fastExtract(String json, String key) {
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) {
            return null;
        }
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) {
            return null;
        }
        int len = json.length();
        int start = colonIdx + 1;
        while (start < len) {
            char c = json.charAt(start);
            if (c != ' ' && c != '\t') {
                break;
            }
            start++;
        }
        if (start >= len) {
            return null;
        }
        char quote = json.charAt(start);
        if (quote != '"' && quote != '\'') {
            return null;
        }
        int end = json.indexOf(quote, start + 1);
        if (end < 0) {
            return null;
        }
        return json.substring(start + 1, end);
    }

    enum Side { NEW, LEGACY }

    record ForwardRequest(String method, String path, String queryString,
                          String contentType, String body, String traceId,
                          String traceparent, String b3) {
        ForwardRequest(String method, String path, String queryString,
                       String contentType, String body, String traceId) {
            this(method, path, queryString, contentType, body, traceId, null, null);
        }
    }

    record SideResult(Side side, Kind kind, String body, long elapsedMs, String error) {

        enum Kind { POSITIVE, NEGATIVE, ERROR, SKIPPED_OPEN }

        static SideResult positive(Side side, String body, long elapsedMs) {
            return new SideResult(side, Kind.POSITIVE, body, elapsedMs, null);
        }

        static SideResult negative(Side side, String body, long elapsedMs) {
            return new SideResult(side, Kind.NEGATIVE, body, elapsedMs, null);
        }

        static SideResult error(Side side, String error, long elapsedMs) {
            return new SideResult(side, Kind.ERROR, null, elapsedMs, error);
        }

        static SideResult skipped(Side side) {
            return new SideResult(side, Kind.SKIPPED_OPEN, null, 0, "breaker open");
        }

        String summary() {
            return switch (kind) {
                case POSITIVE -> "正(" + elapsedMs + "ms)";
                case NEGATIVE -> "负(" + elapsedMs + "ms)";
                case ERROR -> "故障(" + elapsedMs + "ms," + error + ")";
                case SKIPPED_OPEN -> "熔断跳过";
            };
        }
    }

    record MergeOutcome(String winner, String body) {
    }

    record RewriteRule(Pattern pattern, String replacement) {
    }
}
