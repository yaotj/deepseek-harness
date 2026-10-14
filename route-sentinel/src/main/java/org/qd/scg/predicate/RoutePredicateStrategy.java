package org.qd.scg.predicate;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;

import com.alibaba.fastjson2.JSONObject;

@Component
public class RoutePredicateStrategy {
    private final UserRouteService userRouteService;

    public RoutePredicateStrategy(UserRouteService userRouteService) {
        this.userRouteService = userRouteService;
    }

    public RequestPredicate legacyPredicate() {
        return request -> {
            java.util.Optional<String> bizDataOpt = request.param("bizData");
            String bizData = bizDataOpt.orElse(null);
            if (bizData == null) return false;
            try {
                JSONObject json = JSONObject.parseObject(bizData);
                String thirdUserId = json.getString("thirdUserId");
                return thirdUserId != null && userRouteService.isLegacy(thirdUserId);
            } catch (Exception e) {
                return false;
            }
        };
    }

    public RequestPredicate fallbackPredicate() {
        return request -> true;
    }
}
