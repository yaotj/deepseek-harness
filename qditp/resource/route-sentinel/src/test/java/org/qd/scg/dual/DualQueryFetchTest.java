package org.qd.scg.dual;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.qd.scg.dual.DualQueryService.ForwardRequest;
import org.qd.scg.dual.DualQueryService.Side;
import org.qd.scg.dual.DualQueryService.SideResult;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DualQueryFetchTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private DualQueryService newService() {
        DualQueryProperties props = new DualQueryProperties();
        props.setTimeoutMs(800);
        props.setConnectTimeoutMs(300);
        return new DualQueryService(props, null);
    }

    private String startServer(int status, String body) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/itptvm/ci/tvm/requestTakeTicketAuth", exchange -> {
                byte[] resp = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            });
            server.start();
            return "http://127.0.0.1:" + server.getAddress().getPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ForwardRequest postRequest() {
        return new ForwardRequest("POST", "/itptvm/ci/tvm/requestTakeTicketAuth", null,
                "application/x-www-form-urlencoded;charset=UTF-8",
                "providerId=01&bizData={\"deviceId\":\"02441101\"}", "trace-1");
    }

    @Test
    @DisplayName("200 + retCode=0000 + orderNo → 正向")
    void positiveResponse() {
        String base = startServer(200, "{\"orderNo\":\"00202611100000000001\",\"retCode\":\"0000\"}");
        SideResult res = newService().fetchSide(Side.NEW, base, postRequest());
        assertEquals(SideResult.Kind.POSITIVE, res.kind());
    }

    @Test
    @DisplayName("200 + retCode=2003 → 负向")
    void negativeResponse() {
        String base = startServer(200, "{\"orderNo\":null,\"retCode\":\"2003\",\"retMsg\":\"无激活的订单\"}");
        SideResult res = newService().fetchSide(Side.LEGACY, base, postRequest());
        assertEquals(SideResult.Kind.NEGATIVE, res.kind());
    }

    @Test
    @DisplayName("非 200 → 故障并计入熔断")
    void non200IsError() {
        String base = startServer(500, "boom");
        DualQueryService service = newService();
        SideResult res = service.fetchSide(Side.LEGACY, base, postRequest());
        assertEquals(SideResult.Kind.ERROR, res.kind());
    }

    @Test
    @DisplayName("目标不可达 → 故障而不是抛异常")
    void unreachableIsError() {
        SideResult res = newService().fetchSide(Side.NEW, "http://127.0.0.1:1", postRequest());
        assertEquals(SideResult.Kind.ERROR, res.kind());
    }
}
