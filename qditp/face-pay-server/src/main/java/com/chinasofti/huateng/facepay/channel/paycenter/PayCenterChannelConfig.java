package com.chinasofti.huateng.facepay.channel.paycenter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付中心通道装配。
 *
 * <p>{@link PayCenterSigner} 与 {@link PayCenterMessageFactory} 故意<b>不</b>用
 * {@code @Component}：它们是纯 Java 类，报文与待签串的口径必须能在不起 Spring 的单测里逐字比对
 * （设计文档 §七）。装配集中在本类，配置项的读取点也只有这一处。</p>
 */
@Configuration
@EnableConfigurationProperties(PayCenterProperties.class)
public class PayCenterChannelConfig {

    @Bean
    public PayCenterSigner payCenterSigner(PayCenterProperties properties) {
        return new PayCenterSigner(properties.getPrivateKey(), properties.getSignAlgorithm(),
                properties.getJhmKey());
    }

    @Bean
    public PayCenterMessageFactory payCenterMessageFactory(PayCenterProperties properties,
                                                          PayCenterSigner signer) {
        return new PayCenterMessageFactory(properties, signer);
    }
}
