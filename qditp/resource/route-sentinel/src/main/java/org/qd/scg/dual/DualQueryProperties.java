package org.qd.scg.dual;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "dualquery")
public class DualQueryProperties {

    private boolean enabled = true;

    private List<String> paths = List.of("/itptvm/ci/tvm/requestTakeTicketAuth");

    private long timeoutMs = 2500;

    private long connectTimeoutMs = 1000;

    private int breakerFailureThreshold = 3;

    private long breakerOpenMillis = 60_000;

    private String newRouteId = "face-pay-split-route-tvm";

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
