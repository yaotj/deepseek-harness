package org.qd.scg.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class GatewayRouteSyncService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GatewayRouteSyncService.class);
    private final JdbcTemplate jdbc;

    public GatewayRouteSyncService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RouteDef(String id, String uri, boolean legacy) {
    }

    public List<RouteDef> loadRoutes() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT route_id, uri, predicates, order_no FROM gateway_route_config ORDER BY order_no");
            if (rows.isEmpty()) {
                log.info("GATEWAY_ROUTE_CONFIG 空，后续靠 Actuator 注入");
                return List.of();
            }
            List<RouteDef> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                String routeId = col(r, "ROUTE_ID");
                String uri = col(r, "URI");
                String predicates = col(r, "PREDICATES");
                result.add(new RouteDef(routeId, uri, isLegacyRoute(routeId, predicates)));
            }
            log.info("加载 {} 条路由", result.size());
            return result;
        } catch (Exception e) {
            log.warn("加载路由失败: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean isLegacyRoute(String routeId, String predicates) {
        if ("legacy-route".equals(routeId)) {
            return true;
        }
        return predicates != null && predicates.toLowerCase().contains("\"legacy\"");
    }

    private static String col(Map<String, Object> row, String name) {
        Object v = row.get(name);
        if (v == null) v = row.get(name.toUpperCase());
        if (v == null) v = row.get(name.toLowerCase());
        return v == null ? null : String.valueOf(v);
    }

    public void syncRoute(String routeId, String uri, String predicates, String operator, String remark) {
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT uri, predicates FROM gateway_route_config WHERE route_id = ?", routeId);
        Map<String, Object> before = existing.isEmpty() ? null : existing.get(0);
        String beforeUri = before != null && before.get("URI") != null ? String.valueOf(before.get("URI")) : null;
        String beforePreds = before != null && before.get("PREDICATES") != null ? String.valueOf(before.get("PREDICATES")) : null;

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM gateway_route_config WHERE route_id = ?", Integer.class, routeId);
        boolean exists = count != null && count > 0;
        if (exists) {
            jdbc.update(
                "UPDATE gateway_route_config SET uri = ?, predicates = ?, filters = '[]', updated_tms = SYSTIMESTAMP, created_by = COALESCE(?, created_by) WHERE route_id = ?",
                uri, predicates, operator, routeId);
        } else {
            Integer maxOrder = jdbc.queryForObject(
                "SELECT MAX(order_no) FROM gateway_route_config", Integer.class);
            int orderNo = maxOrder == null ? 0 : maxOrder + 1;
            jdbc.update(
                "INSERT INTO gateway_route_config (route_id, uri, predicates, filters, order_no, created_tms, updated_tms, created_by) " +
                "VALUES (?, ?, ?, '[]', ?, SYSTIMESTAMP, SYSTIMESTAMP, COALESCE(?, 'system'))",
                routeId, uri, predicates, orderNo, operator);
        }

        insertAudit(routeId, before == null ? "CREATE" : "UPDATE", beforeUri, uri, beforePreds, predicates, operator, remark);
        log.info("syncRoute OK: {} {}", routeId, uri);
    }

    public void deleteRoute(String routeId, String operator, String remark) {
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT uri, predicates FROM gateway_route_config WHERE route_id = ?", routeId);
        Map<String, Object> before = existing.isEmpty() ? null : existing.get(0);
        String beforeUri = before != null && before.get("URI") != null ? String.valueOf(before.get("URI")) : null;
        String beforePreds = before != null && before.get("PREDICATES") != null ? String.valueOf(before.get("PREDICATES")) : null;
        jdbc.update("UPDATE gateway_route_config SET uri = '', predicates = '[]', updated_tms = SYSTIMESTAMP WHERE route_id = ?", routeId);
        insertAudit(routeId, "DELETE", beforeUri, "", beforePreds, "[]", operator, remark);
        log.info("deleteRoute OK: {}", routeId);
    }

    private void insertAudit(String routeId, String action, String beforeUri, String afterUri,
                             String beforePreds, String afterPreds, String operator, String remark) {
        jdbc.update(
            "INSERT INTO gateway_route_config_audit (route_id, action, before_uri, after_uri, before_predicates, after_predicates, operator, operate_tms, remark) " +
            "VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, 'actuator'), SYSTIMESTAMP, COALESCE(?, 'Actuator 动态维护路由'))",
            routeId, action, beforeUri, afterUri, beforePreds, afterPreds, operator, remark);
    }
}
