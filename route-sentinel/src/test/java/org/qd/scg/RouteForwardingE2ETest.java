package org.qd.scg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RouteForwardingE2ETest {

    @LocalServerPort
    private int port;

    private static final int LEGACY_PORT = 9101;
    private static final int FACE_PAY_PORT = 9102;
    private static final int FEP_APP_PORT = 9103;
    private static volatile boolean running = true;
    private static Thread legacyThread;
    private static Thread facePayThread;
    private static Thread fepAppThread;

    private static final String SPLIT_DEVICE_URL = "/itptvm/ci/tvm/notiTakeTicketResult";
    private static final String SPLIT_BOM_URL = "/itpbom/ci/bom/requestCardDataAnalyse";
    private static final String DEFAULT_DEVICE_URL = "/itptvm/ci/tvm/requestOrder";
    private static final String APP_URL = "/fep-app/ci/app/requestOrder";

    @BeforeAll
    static void startDownstreams() throws Exception {
        running = true;
        legacyThread = startDownstream(LEGACY_PORT, "LEGACY-DOWNSTREAM");
        facePayThread = startDownstream(FACE_PAY_PORT, "FACE-PAY-DOWNSTREAM");
        fepAppThread = startDownstream(FEP_APP_PORT, "FEP-APP-DOWNSTREAM");
        Thread.sleep(800);
    }

    @AfterAll
    static void stopDownstreams() {
        running = false;
        if (legacyThread != null) legacyThread.interrupt();
        if (facePayThread != null) facePayThread.interrupt();
        if (fepAppThread != null) fepAppThread.interrupt();
    }

    private static Thread startDownstream(int port, String body) {
        Thread t = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(port)) {
                while (running) {
                    try (Socket s = ss.accept()) {
                        BufferedReader in = new BufferedReader(
                            new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                        while (true) {
                            String line = in.readLine();
                            if (line == null || line.isEmpty()) break;
                        }
                        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                        String resp = "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: text/plain;charset=UTF-8\r\n"
                            + "Content-Length: " + payload.length + "\r\n"
                            + "Connection: close\r\n\r\n"
                            + body;
                        OutputStream os = s.getOutputStream();
                        os.write(resp.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    } catch (Exception e) {
                        if (running) e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }, "downstream-" + port);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private String hitGateway(String path, String bizDataJson) throws Exception {
        String qs = bizDataJson == null ? "" : "?bizData=" + URLEncoder.encode(bizDataJson, StandardCharsets.UTF_8);
        String url = "http://localhost:" + port + path + qs;
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    @Test
    void registeredUserOnSplitDeviceUrlRoutesToFacePay() throws Exception {
        String body = hitGateway(SPLIT_DEVICE_URL, "{\"thirdUserId\":\"test-in-registry\"}");
        assertEquals("FACE-PAY-DOWNSTREAM", body.trim(),
            "已注册用户在 7 条精确设备 URL 上应被路由到 face-pay-split");
    }

    @Test
    void unregisteredUserOnSplitDeviceUrlRoutesToLegacy() throws Exception {
        String body = hitGateway(SPLIT_DEVICE_URL, "{\"thirdUserId\":\"ghost-user-xyz\"}");
        assertEquals("LEGACY-DOWNSTREAM", body.trim(),
            "未注册用户在 7 条精确设备 URL 上应 fail-safe 到旧 ITP");
    }

    @Test
    void registeredUserOnDefaultDeviceUrlRoutesToFacePay() throws Exception {
        String body = hitGateway(DEFAULT_DEVICE_URL, "{\"thirdUserId\":\"test-in-registry\"}");
        assertEquals("FACE-PAY-DOWNSTREAM", body.trim(),
            "已注册用户在非 7 条的设备域 URL 上应直接透传到 face-pay");
    }

    @Test
    void unregisteredUserOnDefaultDeviceUrlRoutesToFacePay() throws Exception {
        String body = hitGateway(DEFAULT_DEVICE_URL, "{\"thirdUserId\":\"ghost-user-xyz\"}");
        assertEquals("FACE-PAY-DOWNSTREAM", body.trim(),
            "未注册用户在非 7 条的设备域 URL 上应直接透传到 face-pay（不查名单）");
    }

    @Test
    void registeredUserOnAppPathRoutesToFepApp() throws Exception {
        String body = hitGateway(APP_URL, "{\"thirdUserId\":\"test-in-registry\"}");
        assertEquals("FEP-APP-DOWNSTREAM", body.trim(),
            "已注册用户在 APP 域路径应被路由到 fep-app");
    }

    @Test
    void unregisteredUserOnAppPathRoutesToNewITP() throws Exception {
        String body = hitGateway(APP_URL, "{\"thirdUserId\":\"ghost-user-xyz\"}");
        assertEquals("FEP-APP-DOWNSTREAM", body.trim(),
            "未注册用户在 APP 域路径应直接走新 ITP（无需用户判定）");
    }

    @Test
    void missingBizDataOnSplitDeviceUrlRoutesToLegacy() throws Exception {
        String body = hitGateway(SPLIT_DEVICE_URL, null);
        assertEquals("LEGACY-DOWNSTREAM", body.trim(),
            "缺少 bizData 的 7 条精确设备 URL 请求应 fail-safe 到旧 ITP");
    }

    @Test
    void missingBizDataOnDefaultDeviceUrlRoutesToFacePay() throws Exception {
        String body = hitGateway(DEFAULT_DEVICE_URL, null);
        assertEquals("FACE-PAY-DOWNSTREAM", body.trim(),
            "缺少 bizData 的非 7 条设备域请求应直接透传到 face-pay");
    }

    @Test
    void missingBizDataOnAppPathRoutesToNewITP() throws Exception {
        String body = hitGateway(APP_URL, null);
        assertEquals("FEP-APP-DOWNSTREAM", body.trim(),
            "缺少 bizData 的 APP 域请求应直接走新 ITP（无需用户判定）");
    }

    @Test
    void adminEndpointListsRoutes() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/actuator/gatewayroutes")).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), "GET /actuator/gatewayroutes 应返回 200");
        assertTrue(resp.body().contains("legacy-route-tvm"), "列表应含 legacy-route-tvm");
        assertTrue(resp.body().contains("legacy-route-bom"), "列表应含 legacy-route-bom");
        assertTrue(resp.body().contains("face-pay-split-route-tvm"), "列表应含 face-pay-split-route-tvm");
        assertTrue(resp.body().contains("face-pay-split-route-bom"), "列表应含 face-pay-split-route-bom");
        assertTrue(resp.body().contains("face-pay-default-route"), "列表应含 face-pay-default-route");
        assertTrue(resp.body().contains("fep-app-route"), "列表应含 fep-app-route");
    }

    @Test
    void adminEndpointUpsertTriggersLiveReload() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String tvmUrls = "\"Path=/itptvm/ci/tvm/notiTakeTicketResult\",\"Path=/itptvm/ci/tvm/notiTakeTicketFailResult\",\"Path=/itptvm/ci/tvm/requestTakeTicketAuth\"";
        String bomUrls = "\"Path=/itpbom/ci/bom/requestCardDataAnalyse\",\"Path=/itpbom/ci/bom/requestUpdateCardData\"";
        String tvmFilter = "\"RewritePath=/itptvm/ci/tvm/(?<segment>.*),/itptvm/ci/tvmDispatch/${segment}\"";
        String bomFilter = "\"RewritePath=/itpbom/ci/bom/(?<segment>.*),/itpbom/ci/bomDispatch/${segment}\"";
        String swapTvmBody = "{\"uri\":\"http://127.0.0.1:9103\",\"predicates\":[" + tvmUrls + "],\"filters\":[" + tvmFilter + "],\"operator\":\"test\",\"remark\":\"swap\"}";
        String swapBomBody = "{\"uri\":\"http://127.0.0.1:9103\",\"predicates\":[" + bomUrls + "],\"filters\":[" + bomFilter + "],\"operator\":\"test\",\"remark\":\"swap\"}";
        String restoreTvmBody = "{\"uri\":\"http://127.0.0.1:9101\",\"predicates\":[" + tvmUrls + "],\"filters\":[" + tvmFilter + "],\"operator\":\"test\",\"remark\":\"restore\"}";
        String restoreBomBody = "{\"uri\":\"http://127.0.0.1:9101\",\"predicates\":[" + bomUrls + "],\"filters\":[" + bomFilter + "],\"operator\":\"test\",\"remark\":\"restore\"}";
        try {
            String beforeTvm = hitGateway(SPLIT_DEVICE_URL, "{\"thirdUserId\":\"swap-user-xyz\"}");
            assertEquals("LEGACY-DOWNSTREAM", beforeTvm.trim(), "交换前未注册用户在 TVM URL 上应走 legacy 下游 9101");
            String beforeBom = hitGateway(SPLIT_BOM_URL, "{\"thirdUserId\":\"swap-user-xyz\"}");
            assertEquals("LEGACY-DOWNSTREAM", beforeBom.trim(), "交换前未注册用户在 BOM URL 上应走 legacy 下游 9101");

            HttpRequest reqTvm = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/gatewayroutes/legacy-route-tvm"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(swapTvmBody)).build();
            HttpResponse<String> respTvm = client.send(reqTvm, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, respTvm.statusCode(), "POST /actuator/gatewayroutes/legacy-route-tvm 应返回 200");

            HttpRequest reqBom = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/gatewayroutes/legacy-route-bom"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(swapBomBody)).build();
            HttpResponse<String> respBom = client.send(reqBom, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, respBom.statusCode(), "POST /actuator/gatewayroutes/legacy-route-bom 应返回 200");

            String afterTvm = hitGateway(SPLIT_DEVICE_URL, "{\"thirdUserId\":\"swap-user-xyz\"}");
            assertEquals("FEP-APP-DOWNSTREAM", afterTvm.trim(), "reload 后 legacy TVM 车道应指向交换后的 9103");
            String afterBom = hitGateway(SPLIT_BOM_URL, "{\"thirdUserId\":\"swap-user-xyz\"}");
            assertEquals("FEP-APP-DOWNSTREAM", afterBom.trim(), "reload 后 legacy BOM 车道应指向交换后的 9103");
        } finally {
            client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/gatewayroutes/legacy-route-tvm"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(restoreTvmBody)).build(),
                HttpResponse.BodyHandlers.ofString());
            client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/gatewayroutes/legacy-route-bom"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(restoreBomBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        }
    }
}
