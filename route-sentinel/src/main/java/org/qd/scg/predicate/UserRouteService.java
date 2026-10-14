package org.qd.scg.predicate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserRouteService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserRouteService.class);
    private final JdbcTemplate jdbc;

    public UserRouteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isLegacy(String thirdUserId) {
        if (thirdUserId == null) return true;
        try {
            Integer count = jdbc.queryForObject(
                "SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM user_itp_reg_info WHERE third_user_id = ?",
                Integer.class, thirdUserId);
            return count == null || count == 0;
        } catch (Exception e) {
            log.warn("路由查询失败，默认走旧 ITP: {}", e.getMessage());
            return true;
        }
    }
}
