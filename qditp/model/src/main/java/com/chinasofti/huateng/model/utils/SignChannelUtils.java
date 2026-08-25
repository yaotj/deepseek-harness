package com.chinasofti.huateng.model.utils;

/**
 * 签约渠道代码解析工具。
 * 兼容闸机原始报文（0x17）与 account 返回值（17）两种格式。
 */
public final class SignChannelUtils {

    private SignChannelUtils() {
    }

    /**
     * 解析 signChannelCode，统一返回前2位十六进制值（无前缀）。
     * 输入 "0x17" → "17"，输入 "17" → "17"，输入 "1" → "1"，null/空 → null
     */
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
