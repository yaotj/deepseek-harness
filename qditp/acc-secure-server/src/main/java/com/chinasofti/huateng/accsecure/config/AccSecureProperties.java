package com.chinasofti.huateng.accsecure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "acc.secure")
public class AccSecureProperties {

    private String baseUrl;


    private String providerId = "06";


    private String charset = "UTF-8";


    private String format = "json";


    private String deviceId = "ITP-ACC-SECURE";


    private String signType = "00";


    private String signKey = "";


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

        private String requestQrLogicNumList = "/ci/itp/requestQrLoigcNumList";


        private String requestCaKey = "/ci/itp/requestCaKey";


        private String requestUserSm2Key = "/ci/itp/requestUserSm2Key";


        private String requestSignPubkey = "/ci/itp/requestSignPubkey";


        private String requestExportUserPriKey = "/ci/itp/requestExportUserPriKey";


        private String requestSignInsData = "/ci/itp/requestSignInsData";


        private String requestDpk = "/ci/itp/requestDPK";


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
