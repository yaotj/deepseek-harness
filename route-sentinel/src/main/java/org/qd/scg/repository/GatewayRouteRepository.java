package org.qd.scg.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.qd.scg.model.RouteDef;
import org.qd.scg.model.RouteSnapshot;
import org.qd.scg.route.legacy.LegacyRouteStrategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GatewayRouteRepository {

    private final JdbcTemplate jdbc;
    private final LegacyRouteStrategy legacyRouteStrategy;

    public GatewayRouteRepository(JdbcTemplate jdbc, LegacyRouteStrategy legacyRouteStrategy) {
        this.jdbc = jdbc;
        this.legacyRouteStrategy = legacyRouteStrategy;
    }

    public List<RouteDef> findAllOrdered() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT route_id, uri, predicates, order_no FROM gateway_route_config ORDER BY order_no");
        List<RouteDef> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String routeId = col(r, "ROUTE_ID");
            String uri = col(r, "URI");
            String predicates = col(r, "PREDICATES");
            result.add(new RouteDef(routeId, uri, predicates, legacyRouteStrategy.isLegacy(routeId, predicates)));
        }
        return result;
    }

    public Optional<RouteSnapshot> findById(String routeId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT uri, predicates FROM gateway_route_config WHERE route_id = ?", routeId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        String uri = row.get("URI") != null ? String.valueOf(row.get("URI")) : null;
        String predicates = row.get("PREDICATES") != null ? String.valueOf(row.get("PREDICATES")) : null;
        return Optional.of(new RouteSnapshot(uri, predicates));
    }

    public boolean existsById(String routeId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM gateway_route_config WHERE route_id = ?", Integer.class, routeId);
        return count != null && count > 0;
    }

    public int nextOrderNo() {
        Integer maxOrder = jdbc.queryForObject(
            "SELECT MAX(order_no) FROM gateway_route_config", Integer.class);
        return maxOrder == null ? 0 : maxOrder + 1;
    }

    public void update(String routeId, String uri, String predicates, String operator) {
        jdbc.update(
            "UPDATE gateway_route_config SET uri = ?, predicates = ?, filters = '[]', updated_tms = SYSTIMESTAMP, created_by = COALESCE(?, created_by) WHERE route_id = ?",
            uri, predicates, operator, routeId);
    }

    public void insert(String routeId, String uri, String predicates, int orderNo, String operator) {
        jdbc.update(
            "INSERT INTO gateway_route_config (route_id, uri, predicates, filters, order_no, created_tms, updated_tms, created_by) " +
            "VALUES (?, ?, ?, '[]', ?, SYSTIMESTAMP, SYSTIMESTAMP, COALESCE(?, 'system'))",
            routeId, uri, predicates, orderNo, operator);
    }

    public void markDeleted(String routeId) {
        jdbc.update(
            "UPDATE gateway_route_config SET uri = '', predicates = '[]', updated_tms = SYSTIMESTAMP WHERE route_id = ?",
            routeId);
    }

    private static String col(Map<String, Object> row, String name) {
        Object v = row.get(name);
        if (v == null) v = row.get(name.toUpperCase());
        if (v == null) v = row.get(name.toLowerCase());
        return v == null ? null : String.valueOf(v);
    }
}
