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
        assertTrue(resp.body().contains("legacy-route"), "列表应含 legacy-route");
        assertTrue(resp.body().contains("face-pay-split-route"), "列表应含 face-pay-split-route");
        assertTrue(resp.body().contains("face-pay-default-route"), "列表应含 face-pay-default-route");
        assertTrue(resp.body().contains("fep-app-route"), "列表应含 fep-app-route");
    }

    @Test
    void adminEndpointUpsertTriggersLiveReload() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String exact7Urls = "\"Path=/itptvm/ci/tvm/notiTakeTicketResult\",\"Path=/itptvm/ci/tvm/notiTakeTicketFailResult\",\"Path=/itptvm/ci/tvm/requestTakeTicketAuth\",\"Path=/itpbom/ci/bom/notiTakeTicketResult\",\"Path=/itpbom/ci/bom/notiTakeTicketFailResult\",\"Path=/itpbom/ci/bom/requestCardDataAnalyse\",\"Path=/itpbom/ci/bom/requestUpdateCardData\"";
        String swapBody = "{\"uri\":\"http://127.0.0.1:9103\",\"predicates\":[" + exact7Urls + "],\"operator\":\"test\",\"remark\":\"swap\"}";
        String restoreBody = "{\"uri\":\"http://127.0.0.1:9101\",\"predicates\":[" + exact7Urls + ",\"Path=/fep-app/**\"],\"operator\":\"test\",\"remark\":\"restore\"}";
        try {
            String before = hitGateway(SPLIT_DEVICE_URL, "{\"thirdUserId\":\"swap-user-xyz\"}");
            assertEquals("LEGACY-DOWNSTREAM", before.trim(), "交换前未注册用户在 7 条 URL 上应走 legacy 下游 9101");
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/gatewayroutes/legacy-route"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(swapBody)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "POST /actuator/gatewayroutes/legacy-route 应返回 200");
            String after = hitGateway(SPLIT_DEVICE_URL, "{\"thirdUserId\":\"swap-user-xyz\"}");
            assertEquals("FEP-APP-DOWNSTREAM", after.trim(), "reload 后 legacy 车道在 7 条 URL 上应指向交换后的 9103");
        } finally {
            HttpRequest restore = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/gatewayroutes/legacy-route"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(restoreBody)).build();
            client.send(restore, HttpResponse.BodyHandlers.ofString());
        }
    }
}
