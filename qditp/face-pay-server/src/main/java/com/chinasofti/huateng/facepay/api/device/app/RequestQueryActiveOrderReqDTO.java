package com.chinasofti.huateng.facepay.api.device.app;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** 获取已激活取票订单列表入参。 */
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
