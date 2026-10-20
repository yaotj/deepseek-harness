package org.qd.scg.route;

import java.util.List;
import java.util.Optional;

import org.qd.scg.event.RouteChangedEvent;
import org.qd.scg.model.RouteDef;
import org.qd.scg.predicate.RoutePredicateFactory;
import org.qd.scg.predicate.RoutePredicateStrategy;
import org.qd.scg.predicate.UserRouteService;
import org.qd.scg.service.GatewayRouteQueryService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import jakarta.annotation.PostConstruct;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;

@Component
public class UserRouteConfig {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserRouteConfig.class);

    private final RoutePredicateStrategy routePredicateStrategy;
    private final RoutePredicateFactory routePredicateFactory;
    private final GatewayRouteQueryService queryService;
    private final UserRouteService userRouteService;

    private volatile RouterFunction<ServerResponse> current = request -> Optional.empty();

    public UserRouteConfig(RoutePredicateStrategy routePredicateStrategy,
                           RoutePredicateFactory routePredicateFactory,
                           GatewayRouteQueryService queryService,
                           UserRouteService userRouteService) {
        this.routePredicateStrategy = routePredicateStrategy;
        this.routePredicateFactory = routePredicateFactory;
        this.queryService = queryService;
        this.userRouteService = userRouteService;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    @EventListener
    public void onRouteChanged(RouteChangedEvent event) {
        log.info("收到路由变更事件: {} / {}", event.routeId(), event.action());
        reload();
    }

    public void reload() {
        List<RouteDef> routes = queryService.loadRoutes();
        if (routes.isEmpty()) {
            current = request -> Optional.empty();
            log.info("路由表为空，降级为空路由");
            return;
        }
        // 刷新分流路径集合
        userRouteService.reloadSplitPaths();
        RouterFunction<ServerResponse> result = null;
        for (RouteDef r : routes) {
            RequestPredicate pathPredicate = routePredicateFactory.parse(r.predicates());
            RequestPredicate pathOrTrue = pathPredicate != null ? pathPredicate : req -> true;
            RequestPredicate predicate = r.legacy()
                ? routePredicateStrategy.legacyPredicate().and(pathOrTrue)
                : pathOrTrue;

            RouterFunction<ServerResponse> rf = route(r.id())
                .route(predicate, http())
                .before(BeforeFilterFunctions.uri(r.uri()))
                .after(AfterFilterFunctions.addResponseHeader("X-Route-Id", r.id()))
                .build();
            result = result == null ? rf : result.and(rf);
        }
        current = result == null ? request -> Optional.empty() : result;
        log.info("已从 DB 重建 {} 条路由", routes.size());
    }

    @org.springframework.context.annotation.Bean
    public RouterFunction<ServerResponse> userRoute() {
        return (ServerRequest request) -> current.route(request);
    }
}
