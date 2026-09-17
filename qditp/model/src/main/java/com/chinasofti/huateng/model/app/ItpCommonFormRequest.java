package com.chinasofti.huateng.model.app;

/**
 * form-data 入向报文专用的公共请求包装类，全项目唯一实现。
 */
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
