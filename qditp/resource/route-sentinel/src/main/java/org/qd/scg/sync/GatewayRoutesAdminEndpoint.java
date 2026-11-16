package org.qd.scg.sync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.qd.scg.model.RouteDef;
import org.qd.scg.service.GatewayRouteQueryService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "gatewayroutes")
public class GatewayRoutesAdminEndpoint {

    private final GatewayRouteQueryService queryService;

    public GatewayRoutesAdminEndpoint(GatewayRouteQueryService queryService) {
        this.queryService = queryService;
    }

    @ReadOperation
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RouteDef r : queryService.loadRoutes()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("routeId", r.id());
            m.put("uri", r.uri());
            m.put("legacy", r.legacy());
            result.add(m);
        }
        return result;
    }
}
