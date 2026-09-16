package com.chinasofti.huateng.facepay.api.device;

import com.alibaba.fastjson2.JSON;

/**
 * 设备报文解包。{@code bizData} 是 JSON 字符串，需二次反序列化，然后把表单上的公共参数拷进去。
 *
 * <p>逐字复刻旧 {@code TransforUtils.copyBaseParams} 的两个关键行为：</p>
 * <ul>
 *   <li>用 <b>Fastjson2</b> 反序列化 {@code bizData}；</li>
 *   <li>{@code deviceId} <b>只在表单值非空非空白时才覆盖</b>——部分 TVM 报文把 deviceId 放在
 *       bizData 里，无条件覆盖会把它擦成 null。</li>
 * </ul>
 */
public final class DeviceRequests {

    private DeviceRequests() {
    }

    /** {@code bizData} 为空或非法 JSON 时返回 {@code null}，由调用方回 2002。 */
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
