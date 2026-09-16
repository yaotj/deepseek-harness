package com.chinasofti.huateng.account.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 入向报文校验与验签配置（前缀 {@code itp}），供 {@link
 * com.chinasofti.huateng.account.service.AccountRequestVerifier} 使用。
 *
 * <p>ADR-D37 由 4 个散落在 {@code AccountRequestVerifier} 上的 {@code @Value} 收拢成一个对象。
 * <b>配置键与默认值一个字都没改</b>（{@code itp.providerId} / {@code itp.charset} /
 * {@code itp.format} / {@code itp.signKey}），因此 K8s Deployment 的 env 与
 * {@code application.properties} 都不用动。这是本模块第二处 {@code @ConfigurationProperties}，
 * 样板见 {@link EmployeeCardOutboundProperties}。</p>
 *
 * <p><b>⚠️ {@code signKey} 仍带明文默认值，这是本轮刻意保留的既有缺陷</b>（用户 2026-09-11 裁定
 * 「先收成对象、真值暂不动」）。它违反 AGENTS.md §5.2「敏感项 MUST 写 {@code ${ENV_VAR:}}（空默认值）」。
 * <b>上线前 MUST 改成 {@code ${ITP_SIGN_KEY:}} 并轮换该密钥</b>；改的时候连带把
 * {@code account-server/src/main/resources/application.properties} 里的同名真值一起清掉——
 * <b>两处都写了真值，只改一处等于没改</b>。</p>
 *
 * <p><b>NEVER 把这个前缀下的键搬到别的类里用 {@code @Value} 再读一遍</b>：那会让「同一个
 * signKey 有两个读取点」，轮换密钥时必漏一处。</p>
 */
@Component
@ConfigurationProperties(prefix = "itp")
public class ItpSignProperties {

    /** 服务提供方标识，入向报文的 {@code providerId} 必须与之相等。 */
    private String providerId = "01";

    /** 入向报文允许的字符集，比较时忽略大小写。 */
    private String charset = "UTF-8";

    /** 入向报文允许的格式，比较时忽略大小写。 */
    private String format = "json";

    /**
     * 摘要式验签的盐值，拼在签名源串末尾的 {@code &key=} 之后。
     *
     * <p><b>默认值是明文真值，上线前 MUST 换成环境变量注入</b>，见类注释。</p>
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
