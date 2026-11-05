package com.chinasofti.huateng.facepay.api.device;

import com.alibaba.fastjson2.JSON;

public final class DeviceRequests {

    private DeviceRequests() {
    }

    public static <T extends BaseDeviceRequest> T unwrap(BaseDeviceRequest form, Class<T> type) {
        if (form == null || form.getBizData() == null || form.getBizData().isBlank()) {
            return null;
        }
        T request;
        try {
            request = JSON.parseObject(form.getBizData(), type);
        } catch (RuntimeException e) {
            return null;
        }
        if (request == null) {
            return null;
        }
        request.setProviderId(form.getProviderId());
        request.setCharset(form.getCharset());
        request.setFormat(form.getFormat());
        request.setTimestamp(form.getTimestamp());
        if (form.getDeviceId() != null && !form.getDeviceId().isBlank()) {
            request.setDeviceId(form.getDeviceId());
        }
        request.setSignType(form.getSignType());
        request.setSign(form.getSign());
        request.setBizData(form.getBizData());
        return request;
    }
}
