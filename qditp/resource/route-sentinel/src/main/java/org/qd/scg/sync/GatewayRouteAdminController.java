package org.qd.scg.sync;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.qd.scg.service.GatewayRouteCommandService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/actuator/gatewayroutes")
public class GatewayRouteAdminController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GatewayRouteAdminController.class);
    private static final String ADMIN_TOKEN_HEADER = "X-Gateway-Admin-Token";

    private final GatewayRouteCommandService commandService;
    private final ObjectMapper objectMapper;

    @Value("${gateway.admin.token:}")
    private String adminToken;

    @Value("${gateway.admin.allow-ips:}")
    private String adminAllowIps;

    public GatewayRouteAdminController(GatewayRouteCommandService commandService, ObjectMapper objectMapper) {
        this.commandService = commandService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{id}")
    public Map<String, Object> upsert(@PathVariable String id, @RequestBody Map<String, Object> body,
                                      HttpServletRequest request) {
        requireAdmin(request);
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
        Object filtersObj = body != null ? body.get("filters") : null;
        String filters = "[]";
        if (filtersObj != null) {
            try {
                filters = objectMapper.writeValueAsString(filtersObj);
            } catch (Exception e) {
                filters = String.valueOf(filtersObj);
            }
        }
        String operator = body != null ? String.valueOf(body.get("operator")) : null;
        String remark = body != null ? String.valueOf(body.get("remark")) : null;

        commandService.syncRoute(id, uri, predicates, filters, operator, remark);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("routeId", id);
        m.put("uri", uri);
        m.put("status", "synced");
        return m;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id, @RequestBody Map<String, Object> body,
                                      HttpServletRequest request) {
        requireAdmin(request);
        String operator = body != null ? String.valueOf(body.get("operator")) : null;
        String remark = body != null ? String.valueOf(body.get("remark")) : null;

        commandService.deleteRoute(id, operator, remark);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("routeId", id);
        m.put("status", "deleted");
        return m;
    }

    /**
     * 写端点鉴权守卫：token 与来源 IP 白名单任一命中即放行；两者都未配置时拒绝所有写请求（fail-closed）。
     */
    private void requireAdmin(HttpServletRequest request) {
        String configuredToken = adminToken == null ? "" : adminToken.trim();
        Set<String> allowedIps = parseAllowIps();
        boolean tokenConfigured = !configuredToken.isEmpty();
        boolean ipConfigured = !allowedIps.isEmpty();

        boolean tokenOk = tokenConfigured && configuredToken.equals(request.getHeader(ADMIN_TOKEN_HEADER));
        boolean ipOk = ipConfigured && allowedIps.contains(request.getRemoteAddr());
        if (!tokenOk && !ipOk) {
            log.warn("拒绝未授权的路由管理写请求: method={} uri={} remote={}",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private Set<String> parseAllowIps() {
        Set<String> ips = new LinkedHashSet<>();
        if (adminAllowIps == null) {
            return ips;
        }
        for (String part : adminAllowIps.split(",")) {
            String ip = part.trim();
            if (!ip.isEmpty()) {
                ips.add(ip);
            }
        }
        return ips;
    }
}
