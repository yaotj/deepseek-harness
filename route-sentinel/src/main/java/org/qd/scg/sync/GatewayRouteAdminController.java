package org.qd.scg.sync;

import java.util.LinkedHashMap;
import java.util.Map;

import org.qd.scg.route.UserRouteConfig;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson2.JSON;

@RestController
@RequestMapping("/actuator/gatewayroutes")
public class GatewayRouteAdminController {
    private final GatewayRouteSyncService syncService;
    private final UserRouteConfig userRouteConfig;

    public GatewayRouteAdminController(GatewayRouteSyncService syncService, UserRouteConfig userRouteConfig) {
        this.syncService = syncService;
        this.userRouteConfig = userRouteConfig;
    }

    @PostMapping("/{id}")
    public Map<String, Object> upsert(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String uri = body != null ? String.valueOf(body.get("uri")) : null;
        Object predicatesObj = body != null ? body.get("predicates") : null;
        String predicates = predicatesObj == null ? "[]" : JSON.toJSONString(predicatesObj);
        String operator = body != null ? String.valueOf(body.get("operator")) : null;
        String remark = body != null ? String.valueOf(body.get("remark")) : null;
        syncService.syncRoute(id, uri, predicates, operator, remark);
        userRouteConfig.reload();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("routeId", id);
        m.put("uri", uri);
        m.put("status", "synced");
        return m;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String operator = body != null ? String.valueOf(body.get("operator")) : null;
        String remark = body != null ? String.valueOf(body.get("remark")) : null;
        syncService.deleteRoute(id, operator, remark);
        userRouteConfig.reload();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("routeId", id);
        m.put("status", "deleted");
        return m;
    }
}
