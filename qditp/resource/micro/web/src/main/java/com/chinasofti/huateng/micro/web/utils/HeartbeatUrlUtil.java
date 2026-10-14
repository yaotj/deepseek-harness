package com.chinasofti.huateng.micro.web.utils;

/**
 * 设备心跳 / 设备上报类高频接口的 URL 判据，用于在不影响其他日志的前提下抑制心跳刷屏。
 *
 * <p>已覆盖的 URL 变体（不区分大小写）：
 * <ul>
 *   <li>{@code /itpbom/ci/bom/notDeviceHeartbeat}</li>
 *   <li>{@code /token/deviceHeartbeat}、{@code /noteDeviceHeartbeat}（网关双别名路由的内部落点）</li>
 *   <li>{@code /itpbom/ci/bom/notiDeviceHeard}（BomOrderController::notiDeviceHeard）</li>
 * </ul>
 *
 * <p>命中后仅跳过 INFO 级访问日志，错误级（超时 / 5xx）日志不受影响。
 */
public final class HeartbeatUrlUtil {

    private HeartbeatUrlUtil() {
    }

    private static final String[] HEARTBEAT_MARKERS = {
            "deviceheartbeat",
            "deviceheard"
    };

    public static boolean isHeartbeat(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase();
        for (String marker : HEARTBEAT_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
