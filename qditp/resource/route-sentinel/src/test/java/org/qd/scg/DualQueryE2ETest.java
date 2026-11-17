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


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"dualquery.enabled=true", "dualquery.timeout-ms=1500",
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
            assertEquals(OLD_POS_DISPATCH, hitAuth(), "双发 legacy 支路必须应用路由 RewritePath 改写");
        } finally {
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
        String qs = "?bizData=" + URLEncoder.encode("{\"orderNo\":\"test-order-001\"}", StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/itptvm/ci/tvm/requestActiveTicket" + qs))
                .GET().build();
        String body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
        assertTrue(body == null || !body.contains("2003"),
                "写接口不得经过双发归并(双发兜底为 2003)");
    }
}
