package org.qd.scg.service;

import java.util.List;

import org.qd.scg.model.RouteDef;
import org.qd.scg.repository.GatewayRouteRepository;
import org.springframework.stereotype.Service;

@Service
public class GatewayRouteQueryService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GatewayRouteQueryService.class);

    private final GatewayRouteRepository repository;

    public GatewayRouteQueryService(GatewayRouteRepository repository) {
        this.repository = repository;
    }

    public List<RouteDef> loadRoutes() {
        try {
            List<RouteDef> routes = repository.findAllOrdered();
            if (routes.isEmpty()) {
                log.info("GATEWAY_ROUTE_CONFIG 空，后续靠 Actuator 注入");
                return List.of();
            }
            log.info("加载 {} 条路由", routes.size());
            return routes;
        } catch (Exception e) {
            log.warn("加载路由失败: {}", e.getMessage());
            return List.of();
        }
    }
}
