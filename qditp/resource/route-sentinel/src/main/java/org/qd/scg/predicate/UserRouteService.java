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


    public void reloadSplitPaths() {
        try {
            List<?> rows = jdbc.queryForList(
                "SELECT predicates FROM gateway_route_config WHERE predicates IS NOT NULL AND LENGTH(predicates) > 0");
            Set<String> paths = new HashSet<>();
            for (Object row : rows) {
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


    private void extractPathPredicates(String predicatesJson, Set<String> paths) {
        if (predicatesJson == null || predicatesJson.isBlank()) return;
        int i = 0;
        while (i < predicatesJson.length()) {
            int pathIdx = predicatesJson.indexOf("\"Path=", i);
            if (pathIdx < 0) break;
            int start = pathIdx + 6;
            int end = predicatesJson.indexOf("\"", start);
            if (end < 0) break;
            String path = predicatesJson.substring(start, end);
            paths.add(path);
            i = end + 1;
        }
    }


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
