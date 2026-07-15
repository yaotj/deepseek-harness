package com.chinasofti.huateng.acc.security.server.reconnect.thread;

/**
 * Function: Something about of client runtime sign.
 *
 * @author crossoverJie
 * Date: 2020-04-13 02:10
 * @since JDK 1.8
 */
public class ContextHolder {
    private static final ThreadLocal<Boolean> IS_RECONNECT = new ThreadLocal<>();

    public static Boolean getReconnect() {
        return IS_RECONNECT.get();
    }

    public static void setReconnect(boolean reconnect) {
        IS_RECONNECT.set(reconnect);
    }

    public static void clear() {
        IS_RECONNECT.remove();
    }
}
