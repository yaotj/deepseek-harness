package com.chinasofti.huateng.micro.web.utils;


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
