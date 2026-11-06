package org.qd.scg.predicate;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

@Component
public class UserRouteService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserRouteService.class);

    private static final int CACHE_MAX_SIZE = 10_000;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;
    private final LoadingCache<String, Boolean> legacyCache;
    private volatile Set<String> splitPaths = new HashSet<>();

    public UserRouteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.legacyCache = Caffeine.newBuilder()
            .maximumSize(CACHE_MAX_SIZE)
            .expireAfterWrite(CACHE_TTL)
            .recordStats()
            .build(this::loadLegacy);
    }

    @PostConstruct
    public void preload() {
        // 预热失败 MUST 降级而不是阻断启动（与 log 文案一致）：缓存留空，
        // 首个用户查询走 loadLegacy 按需回源，回源失败再 fail-safe 走旧 ITP；
        // reloadSplitPaths 自带 fail-safe（失败置空集合），两条路互不影响。
        try {
            List<String> ids = jdbc.queryForList(
                "SELECT DISTINCT third_user_id FROM user_itp_reg_info", String.class);
            for (String id : ids) {
                legacyCache.put(id, Boolean.FALSE);
            }
            log.info("UserRouteService 缓存预热完成：{} 个用户", ids.size());
        } catch (Exception e) {
            log.error("用户路由缓存预热失败，降级为按用户按需查询（回源失败走旧 ITP）: {}", e.getMessage(), e);
        }
        reloadSplitPaths();
    }

    /**
     * 从数据库重新加载可分流的路径集合（由 Actuator 路由变更触发）
     */
    public void reloadSplitPaths() {
        try {
            List<?> rows = jdbc.queryForList(
                "SELECT predicates FROM gateway_route_config WHERE predicates IS NOT NULL AND LENGTH(predicates) > 0");
            Set<String> paths = new HashSet<>();
            for (Object row : rows) {
                // queryForList 返回的是 Map，需要从 PREDICATES 列取值
                if (row instanceof Map) {
                    Object v = ((Map<?, ?>) row).get("PREDICATES");
                    if (v instanceof String s) {
                        extractPathPredicates(s, paths);
                    }
                } else if (row instanceof String s) {
                    extractPathPredicates(s, paths);
                }
            }
            splitPaths = paths;
            log.info("分流路径集合已刷新：{} 条", paths.size());
        } catch (Exception e) {
            log.warn("加载分流路径失败，使用空集合: {}", e.getMessage());
            splitPaths = new HashSet<>();
        }
    }

    /**
     * 从 predicates JSON 中提取 Path= 定义的路径
     */
    private void extractPathPredicates(String predicatesJson, Set<String> paths) {
        if (predicatesJson == null || predicatesJson.isBlank()) return;
        // 简单解析：提取 "Path=/xxx" 或 "Path=/xxx/**"
        int i = 0;
        while (i < predicatesJson.length()) {
            int pathIdx = predicatesJson.indexOf("\"Path=", i);
            if (pathIdx < 0) break;
            int start = pathIdx + 6; // skip "Path=
            int end = predicatesJson.indexOf("\"", start);
            if (end < 0) break;
            String path = predicatesJson.substring(start, end);
            paths.add(path);
            i = end + 1;
        }
    }

    /**
     * 检查路径是否在分流范围内
     */
    public boolean isSplitPath(String path) {
        if (splitPaths.isEmpty()) return false;
        for (String splitPath : splitPaths) {
            if (path.equals(splitPath) || path.startsWith(splitPath)) {
                return true;
            }
        }
        return false;
    }

    public boolean isLegacy(String thirdUserId) {
        if (thirdUserId == null) {
            return true;
        }
        return Boolean.TRUE.equals(legacyCache.get(thirdUserId));
    }

    /**
     * 判断取票/出票上报请求对应的订单是否属于新 ITP。
     *
     * <p>TVM 取票授权与出票结果上报的 {@code bizData} 不带 {@code cardId}/{@code thirdUserId}，
     * 只能靠 {@code orderNo} 或取票二维码 {@code randomFact} 反查 {@code f2f_order}（新 ITP 订单表）。
     * 查到 → 新 ITP 订单，走新系统；查不到（旧 ITP 订单或无效键）或查询异常 → fail-safe 旧 ITP。
     *
     * @param orderKey {@code orderNo} 或 {@code randomFact}
     * @return true 表示新 ITP 订单
     */
    public boolean hasNewItpOrder(String orderKey) {
        if (orderKey == null || orderKey.isBlank()) {
            return false;
        }
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM f2f_order WHERE order_no = ? OR random_fact = ?",
                Integer.class, orderKey, orderKey);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("订单归属查询失败，默认走旧 ITP: {}", e.getMessage());
            return false;
        }
    }

    public LoadingCache<String, Boolean> getCache() {
        return legacyCache;
    }

    private boolean loadLegacy(String thirdUserId) {
        try {
            Integer marker = jdbc.queryForObject(
                "SELECT 1 FROM user_itp_reg_info WHERE third_user_id = ? AND ROWNUM = 1",
                Integer.class, thirdUserId);
            return marker == null;
        } catch (Exception e) {
            log.warn("路由查询失败，默认走旧 ITP: {}", e.getMessage());
            return true;
        }
    }

    @Scheduled(fixedRate = 30_000)
    public void logCacheStats() {
        var stats = legacyCache.stats();
        log.info("UserRouteService cache stats: hits={}, misses={}, hitRate={}%, size={}, splitPaths={}",
            stats.hitCount(), stats.missCount(),
            String.format(java.util.Locale.US, "%.2f", stats.hitRate() * 100),
            legacyCache.estimatedSize(),
            splitPaths.size());
    }
}
