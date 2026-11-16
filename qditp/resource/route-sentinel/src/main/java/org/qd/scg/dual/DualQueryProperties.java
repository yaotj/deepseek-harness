package org.qd.scg.dual;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 双发择优（scatter-gather）配置。
 *
 * <p>只应用于「只读查询且报文不带用户标识」的设备接口（当前仅 IF2A-08
 * {@code requestTakeTicketAuth}）。写接口（激活、出票上报）NEVER 进双发，
 * 仍走 RoutePredicateStrategy 的订单归属路由。
 */
@Component
@ConfigurationProperties(prefix = "dualquery")
public class DualQueryProperties {

    /** 总开关；false 时回落到订单归属路由（改动前行为）。 */
    private boolean enabled = true;

    /** 参与双发的精确路径。 */
    private List<String> paths = List.of("/itptvm/ci/tvm/requestTakeTicketAuth");

    /** 单侧请求超时（设备总预算 5s，单侧 MUST 控制在 2.5s 量级）。 */
    private long timeoutMs = 2500;

    /** TCP 建连超时。 */
    private long connectTimeoutMs = 1000;

    /** 连续失败多少次后熔断该侧。 */
    private int breakerFailureThreshold = 3;

    /** 熔断打开时长（毫秒），到期后半开试探。 */
    private long breakerOpenMillis = 60_000;

    /** 新 ITP 侧路由 ID（从 GATEWAY_ROUTE_CONFIG 解析目标 URI）。 */
    private String newRouteId = "face-pay-split-route-tvm";

    /** 老 ITP 侧路由 ID。 */
    private String legacyRouteId = "legacy-route-tvm";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getBreakerFailureThreshold() {
        return breakerFailureThreshold;
    }

    public void setBreakerFailureThreshold(int breakerFailureThreshold) {
        this.breakerFailureThreshold = breakerFailureThreshold;
    }

    public long getBreakerOpenMillis() {
        return breakerOpenMillis;
    }

    public void setBreakerOpenMillis(long breakerOpenMillis) {
        this.breakerOpenMillis = breakerOpenMillis;
    }

    public String getNewRouteId() {
        return newRouteId;
    }

    public void setNewRouteId(String newRouteId) {
        this.newRouteId = newRouteId;
    }

    public String getLegacyRouteId() {
        return legacyRouteId;
    }

    public void setLegacyRouteId(String legacyRouteId) {
        this.legacyRouteId = legacyRouteId;
    }
}
