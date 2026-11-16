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
            "SELECT route_id, uri, predicates, filters, order_no FROM gateway_route_config ORDER BY order_no");
        List<RouteDef> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String routeId = col(r, "ROUTE_ID");
            String uri = col(r, "URI");
            String predicates = col(r, "PREDICATES");
            String filters = col(r, "FILTERS");
            result.add(new RouteDef(routeId, uri, predicates, filters, legacyRouteStrategy.isLegacy(routeId, predicates)));
        }
        return result;
    }

    public Optional<RouteSnapshot> findById(String routeId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT uri, predicates, filters FROM gateway_route_config WHERE route_id = ?", routeId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        String uri = row.get("URI") != null ? String.valueOf(row.get("URI")) : null;
        String predicates = row.get("PREDICATES") != null ? String.valueOf(row.get("PREDICATES")) : null;
        String filters = row.get("FILTERS") != null ? String.valueOf(row.get("FILTERS")) : null;
        return Optional.of(new RouteSnapshot(uri, predicates, filters));
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

    public void update(String routeId, String uri, String predicates, String filters, String operator) {
        jdbc.update(
            "UPDATE gateway_route_config SET uri = ?, predicates = ?, filters = COALESCE(?, '[]'), updated_tms = SYSTIMESTAMP, created_by = COALESCE(?, created_by) WHERE route_id = ?",
            uri, predicates, filters, operator, routeId);
    }

    public void insert(String routeId, String uri, String predicates, String filters, int orderNo, String operator) {
        jdbc.update(
            "INSERT INTO gateway_route_config (route_id, uri, predicates, filters, order_no, created_tms, updated_tms, created_by) " +
            "VALUES (?, ?, ?, COALESCE(?, '[]'), ?, SYSTIMESTAMP, SYSTIMESTAMP, COALESCE(?, 'system'))",
            routeId, uri, predicates, filters, orderNo, operator);
    }

    /**
     * 硬删除路由行（2026-11-13 修复，辽阳东 Oracle 实证）。
     *
     * <p>原实现是软删 {@code SET uri='', predicates='[]', filters='[]'}，有两个致命问题：
     * <ol>
     *   <li>Oracle 空串=NULL，撞 URI 的 NOT NULL 约束（ORA-01407），删除恒 500
     *       （H2/MySQL 空串非 NULL，测试环境不暴露）；</li>
     *   <li>软删行仍留在表里，{@code predicates='[]'} 经 RoutePredicateFactory.parse
     *       解析为 null → UserRouteConfig.reload 降级为 {@code req->true} 匹配一切，
     *       空 URI 路由会劫持全部流量。</li>
     * </ol>
     * 历史已由 gateway_route_config_audit 审计表完整记录，NEVER 恢复软删。
     */
    public void deleteById(String routeId) {
        jdbc.update("DELETE FROM gateway_route_config WHERE route_id = ?", routeId);
    }

    private static String col(Map<String, Object> row, String name) {
        Object v = row.get(name);
        if (v == null) v = row.get(name.toUpperCase());
        if (v == null) v = row.get(name.toLowerCase());
        return v == null ? null : String.valueOf(v);
    }
}
