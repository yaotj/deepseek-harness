package com.chinasofti.huateng.model.utils;


public final class SignChannelUtils {

    private SignChannelUtils() {
    }


    public static String resolve(String channel) {
        if (channel == null || channel.trim().isEmpty()) {
            return null;
        }
        String normalized = channel.trim();
        if (normalized.regionMatches(true, 0, "0x", 0, 2)) {
            normalized = normalized.substring(2);
        }
        return normalized.length() >= 2 ? normalized.substring(0, 2) : normalized;
    }
}
