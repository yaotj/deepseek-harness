package org.qd.scg.sync;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class GatewayRouteAuditService {

    private final JdbcTemplate jdbc;

    public GatewayRouteAuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void audit(String routeId, String action, String beforeUri, String afterUri,
                      String beforePredicates, String afterPredicates, String operator, String remark) {
        jdbc.update(
            "INSERT INTO gateway_route_config_audit (route_id, action, before_uri, after_uri, before_predicates, after_predicates, operator, operate_tms, remark) " +
            "VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, 'actuator'), SYSTIMESTAMP, COALESCE(?, 'Actuator 动态维护路由'))",
            routeId, action, beforeUri, afterUri, beforePredicates, afterPredicates, operator, remark);
    }
}
