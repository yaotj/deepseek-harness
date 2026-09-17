package com.chinasofti.huateng.facepay.channel.app;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** APP 通知通道装配。 */
@Configuration
@EnableConfigurationProperties(AppNotifyProperties.class)
public class AppNotifyChannelConfig {
}
