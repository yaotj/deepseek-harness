package org.qd.scg.predicate;

import org.qd.scg.common.RequestConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;

@Component
public class RoutePredicateStrategy {
    private static final Logger log = LoggerFactory.getLogger(RoutePredicateStrategy.class);

    private final BizDataExtractor bizDataExtractor;
    private final UserRouteService userRouteService;

    public RoutePredicateStrategy(BizDataExtractor bizDataExtractor, UserRouteService userRouteService) {
        this.bizDataExtractor = bizDataExtractor;
        this.userRouteService = userRouteService;
    }

    public RequestPredicate legacyPredicate() {
        return request -> {
            String path = request.path();
            if (!userRouteService.isSplitPath(path)) {
                log.debug("分发决策 path={} -> 非分流路径(走默认/新ITP)", path);
                return false;
            }
            String bizData = request.attribute(RequestConstants.BIZDATA_ATTR)
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .orElseGet(() -> request.param("bizData").orElse(null));
            if (bizData == null || bizData.isBlank()) {
                bizData = request.servletRequest().getParameter("bizData");
            }
            String cardId = bizDataExtractor.extractCardIdFast(request);
            String thirdUserId = bizDataExtractor.extractThirdUserIdFast(request);
            String orderKey = isTakeTicketOrderPath(path) ? resolveOrderKey(request) : null;
            boolean legacy;
            if (orderKey != null) {
                boolean newItpOrder = userRouteService.hasNewItpOrder(orderKey);
                legacy = !newItpOrder;
                log.info("分发决策 path={} orderKey={} cardId={} thirdUserId={} -> {}",
                        path, orderKey,
                        cardId == null ? "缺失" : cardId,
                        thirdUserId == null ? "缺失" : thirdUserId,
                        legacy ? "LEGACY(无新ITP订单)" : "NEW(有新ITP订单)");
            } else if (cardId != null) {
                legacy = false;
                log.info("分发决策 path={} cardId={} thirdUserId={} bizData={} -> NEW(cardId)",
                        path, cardId, thirdUserId == null ? "缺失" : thirdUserId, bizData);
            } else if (thirdUserId == null) {
                legacy = true;
                log.info("分发决策 path={} cardId=缺失 thirdUserId=缺失 bizData={} -> LEGACY(兜底)",
                        path, bizData);
            } else {
                legacy = userRouteService.isLegacy(thirdUserId);
                log.info("分发决策 path={} cardId=缺失 thirdUserId={} bizData={} -> {}",
                        path, thirdUserId, bizData, legacy ? "LEGACY" : "NEW");
            }
            return legacy;
        };
    }


    private static boolean isTakeTicketOrderPath(String path) {
        return path != null && (path.endsWith("/requestTakeTicketAuth")
                || path.endsWith("/requestActiveTicket")
                || path.endsWith("/notiTakeTicketResult")
                || path.endsWith("/notiTakeTicketFailResult"));
    }

    private String resolveOrderKey(org.springframework.web.servlet.function.ServerRequest request) {
        String orderNo = bizDataExtractor.extractOrderNoFast(request);
        return orderNo != null ? orderNo : bizDataExtractor.extractRandomFactFast(request);
    }
}
