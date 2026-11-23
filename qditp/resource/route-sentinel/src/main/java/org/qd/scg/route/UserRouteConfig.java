package org.qd.scg.route;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.qd.scg.event.RouteChangedEvent;
import org.qd.scg.model.RouteDef;
import org.qd.scg.dual.DualQueryService;
import org.qd.scg.filter.RouteFilterFactory;
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
    private final RouteFilterFactory routeFilterFactory;
    private final GatewayRouteQueryService queryService;
    private final UserRouteService userRouteService;
    private final DualQueryService dualQueryService;

    private volatile RouterFunction<ServerResponse> current = request -> Optional.empty();

    public UserRouteConfig(RoutePredicateStrategy routePredicateStrategy,
                           RoutePredicateFactory routePredicateFactory,
                           RouteFilterFactory routeFilterFactory,
                           GatewayRouteQueryService queryService,
                           UserRouteService userRouteService,
                           DualQueryService dualQueryService) {
        this.routePredicateStrategy = routePredicateStrategy;
        this.routePredicateFactory = routePredicateFactory;
        this.routeFilterFactory = routeFilterFactory;
        this.queryService = queryService;
        this.userRouteService = userRouteService;
        this.dualQueryService = dualQueryService;
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
        userRouteService.reloadSplitPaths();
        RouterFunction<ServerResponse> result = null;
        int skipped = 0;
        for (RouteDef r : routes) {
            RequestPredicate pathPredicate = routePredicateFactory.parse(r.predicates());
            if (pathPredicate == null) {
                skipped++;
                log.error("路由 {} 的 predicates 解析失败，跳过该路由（不匹配任何请求），predicates={}",
                        r.id(), r.predicates());
                continue;
            }
            RequestPredicate predicate = r.legacy()
                ? routePredicateStrategy.legacyPredicate().and(pathPredicate)
                : pathPredicate;

            var routeBuilder = route(r.id())
                .route(predicate, http())
                .before(BeforeFilterFunctions.uri(r.uri()));

            List<Function<ServerRequest, ServerRequest>> filters = routeFilterFactory.parse(r.filters());
            for (Function<ServerRequest, ServerRequest> filter : filters) {
                routeBuilder.before(filter);
            }

            RouterFunction<ServerResponse> rf = routeBuilder
                .after(AfterFilterFunctions.addResponseHeader("X-Route-Id", r.id()))
                .build();
            result = result == null ? rf : result.and(rf);
        }
        if (result == null) {
            log.error("所有 {} 条路由均解析失败，保留上一次可用的 RouterFunction", routes.size());
            return;
        }
        current = result;
        log.info("已从 DB 重建 {} 条路由，跳过解析失败 {} 条", routes.size() - skipped, skipped);
    }

    @org.springframework.context.annotation.Bean
    public RouterFunction<ServerResponse> userRoute() {
        return (ServerRequest request) -> {
            if (dualQueryService.matches(request.path())) {
                return Optional.of(dualQueryService::handle);
            }
            return current.route(request);
        };
    }
}
