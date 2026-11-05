package com.chinasofti.huateng.facepay.support;

public final class F2fChannel {

    public static final String APP = "01";

    public static final String TVM = "02";

    public static final String BOM = "03";

    public static boolean isSupported(String channel) {
        return APP.equals(channel) || TVM.equals(channel) || BOM.equals(channel);
    }

    public static String fromProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return null;
        }
        return isSupported(providerId) ? providerId : null;
    }

    private F2fChannel() {
    }
}
