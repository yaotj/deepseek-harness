package com.chinasofti.huateng.facepay.support;

public final class F2fDeviceId {

    public static final int STATION_CODE_LENGTH = 4;

    public static String stationCode(String deviceId) {
        if (deviceId == null || deviceId.length() < STATION_CODE_LENGTH) {
            return null;
        }
        return deviceId.substring(0, STATION_CODE_LENGTH);
    }

    private F2fDeviceId() {
    }
}
