package org.qd.scg.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.qd.scg.predicate.RoutePredicateStrategy;
import org.qd.scg.sync.GatewayRouteSyncService;
import org.qd.scg.sync.GatewayRouteSyncService.RouteDef;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import jakarta.annotation.PostConstruct;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.web.servlet.function.RequestPredicates;

@Component
public class UserRouteConfig {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserRouteConfig.class);

    private final RoutePredicateStrategy routePredicateStrategy;
    private final GatewayRouteSyncService syncService;

    private volatile RouterFunction<ServerResponse> current = request -> Optional.empty();

    public UserRouteConfig(RoutePredicateStrategy routePredicateStrategy, GatewayRouteSyncService syncService) {
        this.routePredicateStrategy = routePredicateStrategy;
        this.syncService = syncService;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public void reload() {
        List<RouteDef> routes = syncService.loadRoutes();
        if (routes.isEmpty()) {
            current = request -> Optional.empty();
            log.info("路由表为空，降级为空路由");
            return;
        }
        RouterFunction<ServerResponse> fallback = null;
        RouterFunction<ServerResponse> chained = null;
        for (RouteDef r : routes) {
            RouterFunction<ServerResponse> rf = r.legacy()
                ? route(r.id()).route(routePredicateStrategy.legacyPredicate()
                        .and(RequestPredicates.path("/actuator/**").negate()), http())
                    .before(BeforeFilterFunctions.addRequestHeader("X-Request-Id", java.util.UUID.randomUUID().toString()))
                    .before(BeforeFilterFunctions.uri(r.uri())).build()
                : route(r.id()).route(routePredicateStrategy.fallbackPredicate()
                        .and(RequestPredicates.path("/actuator/**").negate()), http())
                    .before(BeforeFilterFunctions.addRequestHeader("X-Request-Id", java.util.UUID.randomUUID().toString()))
                    .before(BeforeFilterFunctions.uri(r.uri())).build();
            if (r.legacy()) {
                chained = chained == null ? rf : chained.and(rf);
            } else {
                fallback = rf;
            }
        }
        RouterFunction<ServerResponse> result = chained;
        if (fallback != null) {
            result = result == null ? fallback : result.and(fallback);
        }
        current = result == null ? request -> Optional.empty() : result;
        log.info("已从 DB 重建 {} 条路由", routes.size());
    }

    @org.springframework.context.annotation.Bean
    public RouterFunction<ServerResponse> userRoute() {
        return (ServerRequest request) -> current.route(request);
    }
}
