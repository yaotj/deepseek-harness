package com.chinasofti.huateng.model.app;

/**
 * form-data 入向报文专用的公共请求包装类，全项目唯一实现。
 *
 * <p>与 {@link ItpCommonRequest} 的唯一区别是 bizData 固定为 String：公共参数平铺在
 * form-data 中，业务参数以 JSON 字符串提交，由各模块入口自行反序列化为业务 DTO。</p>
 *
 * <p>原先 fep-app-server、fep-acc-server（继承式）与 fep-dev-server、fep-alipay-server
 * （平铺式）各有一份 CommonFormRequest，已于 2026-09-11 全部收口到本类。</p>
 *
 * <p>与父类一致：<b>本类不承载签名语义</b>，验签由各链路自行负责。</p>
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
