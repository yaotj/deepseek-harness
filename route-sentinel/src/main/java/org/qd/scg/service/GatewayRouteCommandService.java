package org.qd.scg.service;

import org.qd.scg.event.RouteChangedEvent;
import org.qd.scg.model.RouteSnapshot;
import org.qd.scg.repository.GatewayRouteRepository;
import org.qd.scg.sync.GatewayRouteAuditService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class GatewayRouteCommandService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GatewayRouteCommandService.class);

    private final GatewayRouteRepository repository;
    private final GatewayRouteAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public GatewayRouteCommandService(GatewayRouteRepository repository,
                                      GatewayRouteAuditService auditService,
                                      ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    public void syncRoute(String routeId, String uri, String predicates, String filters, String operator, String remark) {
        RouteSnapshot before = repository.findById(routeId).orElse(null);
        String beforeUri = before != null ? before.uri() : null;
        String beforePreds = before != null ? before.predicates() : null;
        String beforeFilters = before != null ? before.filters() : null;

        if (repository.existsById(routeId)) {
            repository.update(routeId, uri, predicates, filters, operator);
        } else {
            int orderNo = repository.nextOrderNo();
            repository.insert(routeId, uri, predicates, filters, orderNo, operator);
        }

        auditService.audit(routeId, before == null ? "CREATE" : "UPDATE",
                           beforeUri, uri, beforePreds, predicates, beforeFilters, filters, operator, remark);
        log.info("syncRoute OK: {} {} filters={}", routeId, uri, filters);
        publishChanged(routeId, before == null ? "CREATE" : "UPDATE");
    }

    public void deleteRoute(String routeId, String operator, String remark) {
        RouteSnapshot before = repository.findById(routeId).orElse(null);
        String beforeUri = before != null ? before.uri() : null;
        String beforePreds = before != null ? before.predicates() : null;
        String beforeFilters = before != null ? before.filters() : null;

        repository.markDeleted(routeId);
        auditService.audit(routeId, "DELETE", beforeUri, "", beforePreds, "[]", beforeFilters, "[]", operator, remark);
        log.info("deleteRoute OK: {}", routeId);
        publishChanged(routeId, "DELETE");
    }

    private void publishChanged(String routeId, String action) {
        eventPublisher.publishEvent(new RouteChangedEvent(routeId, action));
    }
}
