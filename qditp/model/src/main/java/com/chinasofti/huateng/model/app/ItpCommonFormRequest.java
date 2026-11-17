package com.chinasofti.huateng.model.app;


public class ItpCommonFormRequest extends ItpCommonRequest<String> {

    @Override
    public String toString() {
        return "ItpCommonFormRequest{" +
                "providerId='" + getProviderId() + '\'' +
                ", charset='" + getCharset() + '\'' +
                ", format='" + getFormat() + '\'' +
                ", timestamp='" + getTimestamp() + '\'' +
                ", deviceId='" + getDeviceId() + '\'' +
                ", signType='" + getSignType() + '\'' +
                ", sign='***'" +
                ", bizData='" + getBizData() + '\'' +
                '}';
    }
}
