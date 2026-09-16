package com.chinasofti.huateng.facepay.channel.app;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * APP 通知通道装配。只负责把 {@link AppNotifyProperties} 注册成 Bean——
 * {@link AppNotifyClient} 自带 {@code @Component}，因为它没有「必须能脱离 Spring 单测」的要求
 * （不像 {@code PayCenterSigner} 要逐字比对待签串）。
 */
@Configuration
@EnableConfigurationProperties(AppNotifyProperties.class)
public class AppNotifyChannelConfig {
}
