package org.qd.scg.sync;

import java.util.LinkedHashMap;
import java.util.Map;

import org.qd.scg.predicate.UserRouteService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "routecache")
public class RouteCacheAdminEndpoint {

    private final UserRouteService userRouteService;

    public RouteCacheAdminEndpoint(UserRouteService userRouteService) {
        this.userRouteService = userRouteService;
    }

    @ReadOperation
    public Map<String, Object> stats() {
        var cache = userRouteService.getCache();
        var stats = cache.stats();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hitCount", stats.hitCount());
        m.put("missCount", stats.missCount());
        m.put("hitRate", String.format("%.2f%%", stats.hitRate() * 100));
        m.put("evictionCount", stats.evictionCount());
        m.put("loadCount", stats.loadCount());
        m.put("loadFailureCount", stats.loadFailureCount());
        m.put("totalLoadTime", stats.totalLoadTime());
        m.put("size", cache.estimatedSize());
        return m;
    }
}
