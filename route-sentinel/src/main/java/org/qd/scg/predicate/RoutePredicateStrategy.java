package org.qd.scg.predicate;

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
            String bizData = request.param("bizData").orElse(null);
            String thirdUserId = bizDataExtractor.extractThirdUserIdFast(request);
            boolean legacy;
            if (thirdUserId == null) {
                // bizData 缺失：fail-safe 到旧 ITP（与历史行为一致）
                legacy = true;
                log.info("分发决策 path={} bizData={} thirdUserId=缺失 -> LEGACY(兜底)", path, bizData);
            } else {
                legacy = userRouteService.isLegacy(thirdUserId);
                log.info("分发决策 path={} thirdUserId={} bizData={} -> {}",
                        path, thirdUserId, bizData, legacy ? "LEGACY" : "NEW");
            }
            return legacy;
        };
    }
}
