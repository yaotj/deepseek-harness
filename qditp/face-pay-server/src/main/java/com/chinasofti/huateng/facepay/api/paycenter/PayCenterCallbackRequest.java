package com.chinasofti.huateng.facepay.api.paycenter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** 支付中心回调的公共报文（信封），六个字段对齐网关文档「公共请求参数」 （{@code docs/external/支付中心网关接口文档.md} 第 34~41 行）。 */
public class PayCenterCallbackRequest {

    private String merchantNo;

    private String apiVersion;

    private String signType;

    private String sign;

    private String charset;

    private String bizData;

    public String getMerchantNo() {
        return merchantNo;
    }

    public void setMerchantNo(String merchantNo) {
        this.merchantNo = merchantNo;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getBizData() {
        return bizData;
    }

    public void setBizData(String bizData) {
        this.bizData = bizData;
    }

    /**
     * 取业务报文的明文 JSON：先按 Base64 解，解不出再按裸 JSON 兜。
     *
     * @return 明文 JSON；{@code bizData} 为空时返回 null
     */
    public String bizDataJson() {
        if (bizData == null || bizData.isBlank()) {
            return null;
        }
        String raw = bizData.trim();
        if (raw.startsWith("{")) {
            return raw;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8).trim();
            if (decoded.startsWith("{")) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return raw;
    }

    @Override
    public String toString() {
        return "PayCenterCallbackRequest{merchantNo=" + merchantNo
                + ", apiVersion=" + apiVersion
                + ", signType=" + signType
                + ", charset=" + charset
                + ", bizData=" + bizData
                + '}';
    }
}
