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
    private static final int NEW_PORT = 9102;
    private static volatile boolean running = true;
    private static Thread legacyThread;
    private static Thread newThread;

    @BeforeAll
    static void startDownstreams() throws Exception {
        running = true;
        legacyThread = startDownstream(LEGACY_PORT, "LEGACY-DOWNSTREAM");
        newThread = startDownstream(NEW_PORT, "NEW-DOWNSTREAM");
        Thread.sleep(600);
    }

    @AfterAll
    static void stopDownstreams() {
        running = false;
        if (legacyThread != null) legacyThread.interrupt();
        if (newThread != null) newThread.interrupt();
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

    private String hitGateway(String bizDataJson) throws Exception {
        String qs = bizDataJson == null ? "" : "?bizData=" + URLEncoder.encode(bizDataJson, StandardCharsets.UTF_8);
        String url = "http://localhost:" + port + "/e2e-probe" + qs;
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    @Test
    void registeredUserRoutesToNewDownstream() throws Exception {
        String body = hitGateway("{\"thirdUserId\":\"test-in-registry\"}");
        assertEquals("NEW-DOWNSTREAM", body.trim(),
            "已注册新 ITP 的用户应被路由到新 ITP 下游");
    }

    @Test
    void unregisteredUserRoutesToLegacyDownstream() throws Exception {
        String body = hitGateway("{\"thirdUserId\":\"ghost-user-xyz\"}");
        assertEquals("LEGACY-DOWNSTREAM", body.trim(),
            "未注册用户应 fail-safe 到旧 ITP 下游");
    }

    @Test
    void missingBizDataRoutesToNewDownstream() throws Exception {
        String body = hitGateway(null);
        assertEquals("NEW-DOWNSTREAM", body.trim(),
            "缺少 bizData 时应落到新 ITP 下游（legacy 谓词不匹配）");
    }

    @Test
    void adminEndpointListsRoutes() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/actuator/gatewayroutes")).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), "GET /actuator/gatewayroutes 应返回 200");
        assertTrue(resp.body().contains("legacy-route"), "列表应含 legacy-route");
        assertTrue(resp.body().contains("new-route"), "列表应含 new-route");
    }

    @Test
    void adminEndpointUpsertTriggersLiveReload() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String swapBody = "{\"uri\":\"http://127.0.0.1:9102\",\"predicates\":[{\"type\":\"legacy\"}],\"operator\":\"test\",\"remark\":\"swap\"}";
        String restoreBody = "{\"uri\":\"http://127.0.0.1:9101\",\"predicates\":[{\"type\":\"legacy\"}],\"operator\":\"test\",\"remark\":\"restore\"}";
        try {
            String before = hitGateway("{\"thirdUserId\":\"swap-user-xyz\"}");
            assertEquals("LEGACY-DOWNSTREAM", before.trim(), "交换前未注册用户应走 legacy 下游 9101");
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/gatewayroutes/legacy-route"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(swapBody)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "POST /actuator/gatewayroutes/legacy-route 应返回 200");
            String after = hitGateway("{\"thirdUserId\":\"swap-user-xyz\"}");
            assertEquals("NEW-DOWNSTREAM", after.trim(), "reload 后 legacy 车道应指向交换后的 9102");
        } finally {
            HttpRequest restore = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/gatewayroutes/legacy-route"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(restoreBody)).build();
            client.send(restore, HttpResponse.BodyHandlers.ofString());
        }
    }
}
