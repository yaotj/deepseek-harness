package com.chinasofti.huateng.acc.security.feign.domain.cardCertificate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author houkepan
 * @date 2020/12/9 15:31
 */
public class CardCertificate {

    /**
     * 证书格式
     */
    @NotBlank(message = "证书格式不能为空")
    @Size(min = 2, max = 2, message = "证书格式长度为2")
    private String cert_format;

    /**
     * 发卡机构标识
     */
    @NotBlank(message = "发卡机构标识不能为空")
    @Size(min = 8, max = 8, message = "发卡机构标识长度为8")
    private String org_id;

    /**
     * 证书失效日期
     */
    @NotBlank(message = "证书失效日期不能为空")
    @Size(min = 4, max = 4, message = "证书失效日期长度为4")
    private String cert_expire_time;

    /**
     * 证书序列号
     */
    @NotBlank(message = "证书序列号不能为空")
    @Size(min = 6, max = 6, message = "证书序列号长度为2")
    private String cert_seq;

    /**
     * 发卡机构公钥签名算法标识
     */
    @NotBlank(message = "发卡机构公钥签名算法标识不能为空")
    @Size(min = 2, max = 2, message = "发卡机构公钥签名算法标识长度为2")
    private String sign_algorithm;

    /**
     * 发卡机构公钥加密算法标识
     */
    @NotBlank(message = "发卡机构公钥加密算法标识不能为空")
    @Size(min = 2, max = 2, message = "发卡机构公钥加密算法标识长度为2")
    private String encrypt_algorithm;

    /**
     * 公钥参数标识
     */
    @NotBlank(message = "公钥参数标识不能为空")
    @Size(min = 2, max = 2, message = "公钥参数标识长度为2")
    private String parameter_id;

    /**
     * 发卡机构公钥模长
     */
    @NotBlank(message = "发卡机构公钥模长不能为空")
    @Size(min = 2, max = 2, message = "发卡机构公钥模长长度为2")
    private String publickey_length;

    /**
     * 发卡机构公钥
     */
    @NotBlank(message = "发卡机构公钥不能为空")
    @Size(min = 44, max = 44, message = "发卡机构公钥长度为44")
    private String publickey;

    public String getCert_format() {
        return cert_format;
    }

    public void setCert_format(String cert_format) {
        this.cert_format = cert_format;
    }

    public String getOrg_id() {
        return org_id;
    }

    public void setOrg_id(String org_id) {
        this.org_id = org_id;
    }

    public String getCert_expire_time() {
        return cert_expire_time;
    }

    public void setCert_expire_time(String cert_expire_time) {
        this.cert_expire_time = cert_expire_time;
    }

    public String getCert_seq() {
        return cert_seq;
    }

    public void setCert_seq(String cert_seq) {
        this.cert_seq = cert_seq;
    }

    public String getSign_algorithm() {
        return sign_algorithm;
    }

    public void setSign_algorithm(String sign_algorithm) {
        this.sign_algorithm = sign_algorithm;
    }

    public String getEncrypt_algorithm() {
        return encrypt_algorithm;
    }

    public void setEncrypt_algorithm(String encrypt_algorithm) {
        this.encrypt_algorithm = encrypt_algorithm;
    }

    public String getParameter_id() {
        return parameter_id;
    }

    public void setParameter_id(String parameter_id) {
        this.parameter_id = parameter_id;
    }

    public String getPublickey_length() {
        return publickey_length;
    }

    public void setPublickey_length(String publickey_length) {
        this.publickey_length = publickey_length;
    }

    public String getPublickey() {
        return publickey;
    }

    public void setPublickey(String publickey) {
        this.publickey = publickey;
    }
}

