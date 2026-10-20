package org.qd.scg.sync;

import java.util.LinkedHashMap;
import java.util.Map;

import org.qd.scg.service.GatewayRouteCommandService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/actuator/gatewayroutes")
public class GatewayRouteAdminController {

    private final GatewayRouteCommandService commandService;
    private final ObjectMapper objectMapper;

    public GatewayRouteAdminController(GatewayRouteCommandService commandService, ObjectMapper objectMapper) {
        this.commandService = commandService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{id}")
    public Map<String, Object> upsert(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String uri = body != null ? String.valueOf(body.get("uri")) : null;
        Object predicatesObj = body != null ? body.get("predicates") : null;
        String predicates = "[]";
        if (predicatesObj != null) {
            try {
                predicates = objectMapper.writeValueAsString(predicatesObj);
            } catch (Exception e) {
                predicates = String.valueOf(predicatesObj);
            }
        }
        String operator = body != null ? String.valueOf(body.get("operator")) : null;
        String remark = body != null ? String.valueOf(body.get("remark")) : null;

        commandService.syncRoute(id, uri, predicates, operator, remark);

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

        commandService.deleteRoute(id, operator, remark);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("routeId", id);
        m.put("status", "deleted");
        return m;
    }
}
