package com.chinasofti.huateng.acc.security.feign.domain.cardCertificate;


public class CardCertificateResult extends CardCertificate {


    private String begin_Identifier;


    private String service_id;


    private String cert_index;


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
