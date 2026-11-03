package org.qd.scg.route.legacy;

import org.qd.scg.common.RouteConstants;
import org.springframework.stereotype.Component;

@Component
public class LegacyRouteIdStrategy implements LegacyRouteStrategy {

    @Override
    public boolean isLegacy(String routeId, String predicates) {
        return routeId != null && routeId.startsWith(RouteConstants.LEGACY_ROUTE_ID_PREFIX);
    }
}
