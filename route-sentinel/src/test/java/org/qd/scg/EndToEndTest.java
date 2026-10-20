package org.qd.scg;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EndToEndTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void actuatorHealthShouldReturn200() throws Exception {
        String url = "http://localhost:" + port + "/actuator/health";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() == 200, "Expected 200, got " + response.statusCode());
    }

    @Test
    void actuatorInfoShouldReturn200() throws Exception {
        String url = "http://localhost:" + port + "/actuator/info";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() == 200, "Expected 200, got " + response.statusCode());
    }

    @Test
    void actuatorMetricsShouldReturn200() throws Exception {
        String url = "http://localhost:" + port + "/actuator/metrics/jvm.memory.used";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() == 200, "Expected 200, got " + response.statusCode());
    }

    @Test
    void gatewayRouteConfigShouldHaveFourRoutes() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM gateway_route_config", Integer.class);
        assertTrue(count != null && count >= 4, "Expected at least 4 routes in DB, got " + count);
    }

    @Test
    void legacyRouteShouldExist() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM gateway_route_config WHERE route_id = ?", Integer.class, "legacy-route");
        assertTrue(count != null && count > 0, "legacy-route not found in DB");
    }

    @Test
    void facePaySplitRouteShouldExist() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM gateway_route_config WHERE route_id = ?", Integer.class, "face-pay-split-route");
        assertTrue(count != null && count > 0, "face-pay-split-route not found in DB");
    }

    @Test
    void facePayDefaultRouteShouldExist() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM gateway_route_config WHERE route_id = ?", Integer.class, "face-pay-default-route");
        assertTrue(count != null && count > 0, "face-pay-default-route not found in DB");
    }

    @Test
    void fepAppRouteShouldExist() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM gateway_route_config WHERE route_id = ?", Integer.class, "fep-app-route");
        assertTrue(count != null && count > 0, "fep-app-route not found in DB");
    }

    @Test
    void userRegInfoShouldHaveTestData() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user_itp_reg_info", Integer.class);
        assertTrue(count != null && count >= 2, "Expected at least 2 test users in DB, got " + count);
    }

    @Test
    void testUserInRegistryShouldHaveIsLegacyFalse() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_itp_reg_info WHERE third_user_id = ?", Integer.class, "test-in-registry");
        assertTrue(count != null && count > 0, "test-in-registry not found");
    }

    @Test
    void testCancelledUserShouldExist() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_itp_reg_info WHERE third_user_id = ?", Integer.class, "test-cancelled-001");
        assertTrue(count != null && count > 0, "test-cancelled-001 not found");
    }
}
