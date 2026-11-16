package org.qd.scg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * 双发择优端到端：IF2A-08 同时打新(9102)/老(9101)两侧 mock,按归并矩阵断言网关应答。
 *
 * <p>与 {@link RouteForwardingE2ETest} 互补:那边钉住 dualquery.enabled=false 的归属路由回退,
 * 本类钉住 enabled=true 的双发归并。两侧 mock 应答由 AtomicReference 控制,按用例切换。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"dualquery.enabled=true", "dualquery.timeout-ms=1500",
                // 独立 H2 实例:与 RouteForwardingE2ETest 共用同名内存库时,
                // 第二个上下文的 INIT 脚本会因主键冲突初始化失败,路由表查空全 404
                "spring.datasource.url=jdbc:h2:mem:dual_query_route;MODE=Oracle;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:schema-h2.sql'"})
@ActiveProfiles("test")
class DualQueryE2ETest {

    private static final int LEGACY_PORT = 9101;
    private static final int FACE_PAY_PORT = 9102;
    private static final String AUTH_PATH = "/itptvm/ci/tvm/requestTakeTicketAuth";
    private static final String AUTH_DISPATCH_PATH = "/itptvm/ci/tvmDispatch/requestTakeTicketAuth";

    private static final String NEW_POS = "{\"orderNo\":\"00202611100000000001\",\"retCode\":\"0000\",\"retMsg\":\"成功\"}";
    private static final String OLD_POS = "{\"orderNo\":\"00112611100000000002\",\"retCode\":\"0000\",\"retMsg\":\"成功\"}";
    private static final String OLD_POS_DISPATCH = "{\"orderNo\":\"00112611100000000009\",\"retCode\":\"0000\",\"retMsg\":\"成功-LEGACY-DISPATCH\"}";
    private static final String NEG = "{\"orderNo\":null,\"retCode\":\"2003\",\"retMsg\":\"无激活的订单\"}";

    private static HttpServer legacyServer;
    private static HttpServer facePayServer;
    private static final AtomicReference<String> legacyBody = new AtomicReference<>(NEG);
    private static final AtomicReference<String> facePayBody = new AtomicReference<>(NEG);
    private static final AtomicInteger legacyStatus = new AtomicInteger(200);

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startDownstreams() throws Exception {
        legacyServer = startMock(LEGACY_PORT, legacyBody, legacyStatus);
        facePayServer = startMock(FACE_PAY_PORT, facePayBody, new AtomicInteger(200));
    }

    @AfterAll
    static void stopDownstreams() {
        if (legacyServer != null) legacyServer.stop(0);
        if (facePayServer != null) facePayServer.stop(0);
    }

    private static HttpServer startMock(int listenPort, AtomicReference<String> bodyRef,
                                        AtomicInteger statusRef) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", listenPort), 0);
        // 原路径应答由 bodyRef 控制;dispatch 路径回固定标记体,证明改写后的请求确实落在该路径上
        server.createContext(AUTH_PATH, exchange -> {
            byte[] resp = bodyRef.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(statusRef.get(), resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.createContext(AUTH_DISPATCH_PATH, exchange -> {
            byte[] resp = OLD_POS_DISPATCH.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        return server;
    }

    private String hitAuth() throws Exception {
        String qs = "?bizData=" + URLEncoder.encode(
                "{\"deviceId\":\"02441101\",\"qrcodeGenDate\":\"20261110150000\",\"randomFact\":\"r-1\"}",
                StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + AUTH_PATH + qs)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private void resetMocks() {
        legacyBody.set(NEG);
        facePayBody.set(NEG);
        legacyStatus.set(200);
    }

    @Test
    void newPositiveLegacyNegative_returnsNew() throws Exception {
        resetMocks();
        facePayBody.set(NEW_POS);
        assertEquals(NEW_POS, hitAuth(), "新侧正向应取新侧应答");
    }

    @Test
    void legacyPositiveNewNegative_returnsLegacy() throws Exception {
        resetMocks();
        legacyBody.set(OLD_POS);
        assertEquals(OLD_POS, hitAuth(), "老侧正向(老用户订单)应取老侧应答");
    }

    @Test
    void bothNegative_returnsNewSideNegative() throws Exception {
        resetMocks();
        assertEquals(NEG, hitAuth(), "双负应取新侧负向应答");
    }

    @Test
    void bothPositive_prefersNew() throws Exception {
        resetMocks();
        facePayBody.set(NEW_POS);
        legacyBody.set(OLD_POS);
        assertEquals(NEW_POS, hitAuth(), "双正冲突应取新侧");
    }

    @Test
    void legacyError_newNegative_returnsNewNegative() throws Exception {
        resetMocks();
        legacyStatus.set(500);
        assertEquals(NEG, hitAuth(), "老侧 500 时应回新侧负向,不应放大成网关 5xx");
    }

    @Test
    void legacyError_newPositive_returnsNew() throws Exception {
        resetMocks();
        legacyStatus.set(500);
        facePayBody.set(NEW_POS);
        assertEquals(NEW_POS, hitAuth(), "老侧故障不影响新侧正向应答");
    }

    @Test
    void legacyRewriteFilter_appliesToDualLegacyLeg() throws Exception {
        resetMocks();
        legacyBody.set(OLD_POS);
        // 通过 Actuator 给 legacy-route-tvm 加 RewritePath(模拟生产改路由,不改代码)
        String body = "{\"uri\":\"http://127.0.0.1:9101\",\"predicates\":[\"Path=" + AUTH_PATH + "\"],"
                + "\"filters\":[\"RewritePath=/itptvm/ci/tvm/(?!requestActiveTicket$)(?<segment>.*),/itptvm/ci/tvmDispatch/${segment}\"],"
                + "\"operator\":\"test\",\"remark\":\"tvmDispatch\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/gatewayroutes/legacy-route-tvm"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), "路由更新应成功");
        try {
            // 改写生效后,双发 legacy 支路落在 dispatch 路径上,应答为 dispatch 标记体
            assertEquals(OLD_POS_DISPATCH, hitAuth(), "双发 legacy 支路必须应用路由 RewritePath 改写");
        } finally {
            // 还原:去掉 filter,避免影响同上下文其他用例
            String restore = "{\"uri\":\"http://127.0.0.1:9101\",\"predicates\":[\"Path=" + AUTH_PATH + "\"],"
                    + "\"filters\":[],\"operator\":\"test\",\"remark\":\"restore\"}";
            HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/actuator/gatewayroutes/legacy-route-tvm"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(restore)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void writePathStillUsesAttributionRouting() throws Exception {
        // requestActiveTicket 是写接口,NEVER 双发:orderNo 命中 H2 f2f_order 应只走归属路由。
        // 归属路由的下游是 RouteForwardingE2ETest 的纯文本 mock 语义,这里只断言「应答不含双发兜底负向」。
        String qs = "?bizData=" + URLEncoder.encode("{\"orderNo\":\"test-order-001\"}", StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/itptvm/ci/tvm/requestActiveTicket" + qs))
                .GET().build();
        String body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
        assertTrue(body == null || !body.contains("2003"),
                "写接口不得经过双发归并(双发兜底为 2003)");
    }
}
