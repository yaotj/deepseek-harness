package com.chinasofti.huateng.acc.security.feign.domain.cardCertificate;

/**
 * @author houkepan
 * @date 2020/12/9 15:31
 */
public class CardCertificateResult extends CardCertificate {

    /**
     * 记录头
     */
    private String begin_Identifier;

    /**
     * 服务标识
     */
    private String service_id;

    /**
     * ACC密管中心公钥索引
     */
    private String cert_index;

    /**
     * 证书数字签名
     */
    private String cert_sign;

    public String getBegin_Identifier() {
        return begin_Identifier;
    }

    public void setBegin_Identifier(String begin_Identifier) {
        this.begin_Identifier = begin_Identifier;
    }

    public String getService_id() {
        return service_id;
    }

    public void setService_id(String service_id) {
        this.service_id = service_id;
    }

    public String getCert_index() {
        return cert_index;
    }

    public void setCert_index(String cert_index) {
        this.cert_index = cert_index;
    }

    public String getCert_sign() {
        return cert_sign;
    }

    public void setCert_sign(String cert_sign) {
        this.cert_sign = cert_sign;
    }
}
