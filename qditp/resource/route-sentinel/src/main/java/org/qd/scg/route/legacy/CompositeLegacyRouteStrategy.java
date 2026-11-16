package org.qd.scg.route.legacy;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class CompositeLegacyRouteStrategy implements LegacyRouteStrategy {

    private final LegacyRouteIdStrategy idStrategy;
    private final LegacyPredicateStrategy predicateStrategy;

    public CompositeLegacyRouteStrategy(LegacyRouteIdStrategy idStrategy, LegacyPredicateStrategy predicateStrategy) {
        this.idStrategy = idStrategy;
        this.predicateStrategy = predicateStrategy;
    }

    @Override
    public boolean isLegacy(String routeId, String predicates) {
        return idStrategy.isLegacy(routeId, predicates) || predicateStrategy.isLegacy(routeId, predicates);
    }
}
