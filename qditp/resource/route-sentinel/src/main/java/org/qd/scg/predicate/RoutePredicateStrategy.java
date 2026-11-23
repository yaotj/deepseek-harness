package org.qd.scg.predicate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


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
            String cardId = bizDataExtractor.extractCardIdFast(request);
            String thirdUserId = bizDataExtractor.extractThirdUserIdFast(request);
            String orderKey = isTakeTicketOrderPath(path) ? resolveOrderKey(request) : null;
            boolean legacy;
            if (orderKey != null) {
                boolean newItpOrder = userRouteService.hasNewItpOrder(orderKey);
                legacy = !newItpOrder;
                log.info("分发决策 path={} orderKeyHash={} cardIdHash={} thirdUserIdHash={} -> {}",
                        path, hash(orderKey), hash(cardId), hash(thirdUserId),
                        legacy ? "LEGACY(无新ITP订单)" : "NEW(有新ITP订单)");
            } else if (cardId != null) {
                legacy = false;
                log.info("分发决策 path={} cardIdHash={} thirdUserIdHash={} -> NEW(cardId)",
                        path, hash(cardId), hash(thirdUserId));
            } else if (thirdUserId == null) {
                legacy = true;
                log.info("分发决策 path={} cardId=缺失 thirdUserId=缺失 -> LEGACY(兜底)", path);
            } else {
                legacy = userRouteService.isLegacy(thirdUserId);
                log.info("分发决策 path={} cardId=缺失 thirdUserIdHash={} -> {}",
                        path, hash(thirdUserId), legacy ? "LEGACY" : "NEW");
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

    /** 敏感标识仅打印 SHA-256 前 8 位十六进制，避免 thirdUserId/cardId/orderNo/randomFact 原文入日志。 */
    private static String hash(String value) {
        if (value == null || value.isBlank()) {
            return "缺失";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
                sb.append(Character.forDigit(digest[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "hash-error";
        }
    }
}
