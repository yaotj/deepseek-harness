package com.chinasofti.huateng.account.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 入向报文校验与验签配置（前缀 {@code itp}），供 {@link
 * com.chinasofti.huateng.account.service.AccountRequestVerifier} 使用。
 */
@Component
@ConfigurationProperties(prefix = "itp")
public class ItpSignProperties {
    /**
     * 服务提供方标识，入向报文的 {@code providerId} 必须与之相等。
     */
    private String providerId = "01";

    /**
     * 入向报文允许的字符集，比较时忽略大小写。
     */
    private String charset = "UTF-8";

    /**
     * 入向报文允许的格式，比较时忽略大小写。
     */
    private String format = "json";

    /**
     * 摘要式验签的盐值，拼在签名源串末尾的 {@code &key=} 之后。
     */
    private String signKey = "bc4f7c96259acf9946094fa";

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

    public String getSignKey() {
        return signKey;
    }

    public void setSignKey(String signKey) {
        this.signKey = signKey;
    }
}
