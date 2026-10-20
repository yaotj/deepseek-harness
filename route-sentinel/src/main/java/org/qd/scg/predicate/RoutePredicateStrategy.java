package org.qd.scg.predicate;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;

@Component
public class RoutePredicateStrategy {
    private final BizDataExtractor bizDataExtractor;
    private final UserRouteService userRouteService;

    public RoutePredicateStrategy(BizDataExtractor bizDataExtractor, UserRouteService userRouteService) {
        this.bizDataExtractor = bizDataExtractor;
        this.userRouteService = userRouteService;
    }

    public RequestPredicate legacyPredicate() {
        return request -> {
            String path = request.path();
            // 动态检查路径是否在分流范围内（从 Actuator 路由配置读取）
            if (!userRouteService.isSplitPath(path)) {
                return false;
            }
            String thirdUserId = bizDataExtractor.extractThirdUserIdFast(request);
            if (thirdUserId == null) {
                return true;
            }
            return userRouteService.isLegacy(thirdUserId);
        };
    }
}
