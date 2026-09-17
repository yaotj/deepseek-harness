package com.chinasofti.huateng.facepay.channel.paycenter;

/** 支付中心请求公共报文（信封）。 */
public class PayCenterRequest {

    private String merchantNo;

    private String apiVersion;

    private String signType;

    private String charset;

    private String bizData;

    private String sign;

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

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    /** 打日志只用这个方法：{@code bizData} 与 {@code sign} 只出长度，不出内容。 */
    @Override
    public String toString() {
        return "PayCenterRequest{merchantNo=" + merchantNo
                + ", apiVersion=" + apiVersion
                + ", signType=" + signType
                + ", charset=" + charset
                + ", bizDataLength=" + (bizData == null ? 0 : bizData.length())
                + ", signLength=" + (sign == null ? 0 : sign.length())
                + '}';
    }
}
