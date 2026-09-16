package com.chinasofti.huateng.facepay.api.device.app;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * 获取已激活取票订单列表入参。
 *
 * <p>{@code appType} 只认 {@code 01}（青岛地铁），其余值旧实现回
 * {@code 9999 appType有误，请输入正确的值}，本实现照搬。</p>
 */
public class RequestQueryActiveOrderReqDTO extends BaseDeviceRequest {

    /** 青岛地铁 APP。 */
    public static final String APP_TYPE_QD_METRO = "01";

    private String userId;

    private String appType;

    public boolean isQdMetro() {
        return APP_TYPE_QD_METRO.equals(appType);
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAppType() {
        return appType;
    }

    public void setAppType(String appType) {
        this.appType = appType;
    }

    @Override
    public String toString() {
        return "RequestQueryActiveOrderReqDTO{userId=" + userId + ", appType=" + appType + '}';
    }
}
