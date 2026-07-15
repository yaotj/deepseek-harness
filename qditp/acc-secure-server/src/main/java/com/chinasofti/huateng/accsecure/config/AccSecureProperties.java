package com.chinasofti.huateng.accsecure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ACC 安全接口调用配置。
 */
@ConfigurationProperties(prefix = "acc.secure")
public class AccSecureProperties {
    /**
     * ACC 服务根地址。
     */
    private String baseUrl;

    /**
     * 商户编码。
     */
    private String providerId = "06";

    /**
     * 字符集。
     */
    private String charset = "UTF-8";

    /**
     * 数据格式。
     */
    private String format = "json";

    /**
     * 设备编码。
     */
    private String deviceId = "ITP-ACC-SECURE";

    /**
     * 签名类型。
     */
    private String signType = "00";

    /**
     * MD5 签名 key。
     */
    private String signKey = "";

    /**
     * ACC 接口路径配置。
     */
    private final Paths paths = new Paths();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getSignKey() {
        return signKey;
    }

    public void setSignKey(String signKey) {
        this.signKey = signKey;
    }

    public Paths getPaths() {
        return paths;
    }

    public static class Paths {
        /**
         * IF7B-01 请求逻辑卡号。
         */
        private String requestQrLogicNumList = "/ci/itp/requestQrLoigcNumList";

        /**
         * IF7B-02 请求生成地铁CA密钥。
         */
        private String requestCaKey = "/ci/itp/requestCaKey";

        /**
         * IF7B-03 请求生成用户SM2密钥。
         */
        private String requestUserSm2Key = "/ci/itp/requestUserSm2Key";

        /**
         * IF7B-04 请求签名用户公钥。
         */
        private String requestSignPubkey = "/ci/itp/requestSignPubkey";

        /**
         * IF7B-05 请求导出用户私钥。
         */
        private String requestExportUserPriKey = "/ci/itp/requestExportUserPriKey";

        /**
         * IF7B-06 请求签名行业数据。
         */
        private String requestSignInsData = "/ci/itp/requestSignInsData";

        /**
         * IF7B-07 请求HCE卡片消费密钥。
         */
        private String requestDpk = "/ci/itp/requestDPK";

        /**
         * IF7B-08 请求发售HCE单程票。
         */
        private String requestHecCardDate = "/ci/itp/requestHecCardDate";

        public String getRequestQrLogicNumList() {
            return requestQrLogicNumList;
        }

        public void setRequestQrLogicNumList(String requestQrLogicNumList) {
            this.requestQrLogicNumList = requestQrLogicNumList;
        }

        public String getRequestCaKey() {
            return requestCaKey;
        }

        public void setRequestCaKey(String requestCaKey) {
            this.requestCaKey = requestCaKey;
        }

        public String getRequestUserSm2Key() {
            return requestUserSm2Key;
        }

        public void setRequestUserSm2Key(String requestUserSm2Key) {
            this.requestUserSm2Key = requestUserSm2Key;
        }

        public String getRequestSignPubkey() {
            return requestSignPubkey;
        }

        public void setRequestSignPubkey(String requestSignPubkey) {
            this.requestSignPubkey = requestSignPubkey;
        }

        public String getRequestExportUserPriKey() {
            return requestExportUserPriKey;
        }

        public void setRequestExportUserPriKey(String requestExportUserPriKey) {
            this.requestExportUserPriKey = requestExportUserPriKey;
        }

        public String getRequestSignInsData() {
            return requestSignInsData;
        }

        public void setRequestSignInsData(String requestSignInsData) {
            this.requestSignInsData = requestSignInsData;
        }

        public String getRequestDpk() {
            return requestDpk;
        }

        public void setRequestDpk(String requestDpk) {
            this.requestDpk = requestDpk;
        }

        public String getRequestHecCardDate() {
            return requestHecCardDate;
        }

        public void setRequestHecCardDate(String requestHecCardDate) {
            this.requestHecCardDate = requestHecCardDate;
        }
    }
}
