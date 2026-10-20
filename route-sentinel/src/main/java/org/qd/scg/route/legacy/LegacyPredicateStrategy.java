package org.qd.scg.route.legacy;

import org.springframework.stereotype.Component;

@Component
public class LegacyPredicateStrategy implements LegacyRouteStrategy {

    @Override
    public boolean isLegacy(String routeId, String predicates) {
        return predicates != null && predicates.toLowerCase().contains("\"legacy\"");
    }
}
