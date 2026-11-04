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
            boolean legacy;
            if (cardId != null) {
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
}
