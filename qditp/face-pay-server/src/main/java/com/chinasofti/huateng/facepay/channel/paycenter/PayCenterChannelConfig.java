package com.chinasofti.huateng.facepay.channel.paycenter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 支付中心通道装配。 */
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
