package com.chinasofti.huateng.facepay.api.paycenter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
