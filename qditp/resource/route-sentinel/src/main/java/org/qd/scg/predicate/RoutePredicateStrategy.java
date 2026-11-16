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
            // 非分流路径一律走新 ITP（默认路由），此处不打印以免淹没日志；
            // 分流路径才需判定 旧/新 ITP，是分发正确性核查的关键。
            if (!userRouteService.isSplitPath(path)) {
                log.debug("分发决策 path={} -> 非分流路径(走默认/新ITP)", path);
                return false;
            }
            // bizData 原始串含 thirdUserId / 手机号 / 卡号等字段，原样打印便于核查分发。
            // 优先使用 FormDataCacheFilter 解析后的属性，兼容 POST form body。
            String bizData = request.attribute(RequestConstants.BIZDATA_ATTR)
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .orElseGet(() -> request.param("bizData").orElse(null));
            if (bizData == null || bizData.isBlank()) {
                bizData = request.servletRequest().getParameter("bizData");
            }
            String cardId = bizDataExtractor.extractCardIdFast(request);
            String thirdUserId = bizDataExtractor.extractThirdUserIdFast(request);
            // TVM 取票授权 / 出票结果上报的报文不带用户标识，但带 orderNo 或取票二维码 randomFact，
            // 可反查新 ITP 订单表按"订单归属"分流：有订单走新 ITP，无订单（旧 ITP 订单）走旧 ITP。
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
                // 有 cardId 即视为新 ITP 用户，直接走新系统
                legacy = false;
                log.info("分发决策 path={} cardId={} thirdUserId={} bizData={} -> NEW(cardId)",
                        path, cardId, thirdUserId == null ? "缺失" : thirdUserId, bizData);
            } else if (thirdUserId == null) {
                // bizData 缺失 cardId 与 thirdUserId：fail-safe 到旧 ITP（与历史行为一致）
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

    /**
     * 是否为"按订单归属分流"的 TVM 取票/出票上报路径。
     *
     * <p>这 4 条接口的 {@code bizData} 不带 {@code cardId}/{@code thirdUserId}，但带
     * {@code orderNo} 或取票二维码 {@code randomFact}，可用于反查新 ITP 订单表。
     * {@code requestActiveTicket} 自带 {@code orderNo}（激活取票），可与授权/出票上报一同判定。
     * 用 {@code endsWith} 兼容 {@code /itptvm/ci/tvm} 与 {@code /itpbom/ci/tvm} 两种前缀。
     */
    private static boolean isTakeTicketOrderPath(String path) {
        return path != null && (path.endsWith("/requestTakeTicketAuth")
                || path.endsWith("/requestActiveTicket")
                || path.endsWith("/notiTakeTicketResult")
                || path.endsWith("/notiTakeTicketFailResult"));
    }

    /** 优先取 {@code orderNo}，其次取 {@code randomFact}；都缺失返回 {@code null}。 */
    private String resolveOrderKey(org.springframework.web.servlet.function.ServerRequest request) {
        String orderNo = bizDataExtractor.extractOrderNoFast(request);
        return orderNo != null ? orderNo : bizDataExtractor.extractRandomFactFast(request);
    }
}
