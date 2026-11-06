package com.chinasofti.huateng.micro.monitor.configuration;

import com.chinasofti.huateng.micro.monitor.prometheus.*;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.prometheus.client.CollectorRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class ExporterAutoConfiguration {

    @Autowired
    MonitorConfig monitorConfig;

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "other.monitor.metric.open", havingValue = "true", matchIfMissing = false)
    public CollectorRegistry collectorRegistry() {
        return new CollectorRegistry(true);
    }

    @Value("${spring.application.name}")
    String appName;

    @Bean
    @ConditionalOnProperty(name = "other.monitor.metric.open", havingValue = "true", matchIfMissing = false)
    public WebExport webExport(MeterRegistry registry) {
        return new WebExport(registry, appName, monitorConfig);
    }

    @Bean
    @ConditionalOnProperty(name = "other.monitor.metric.open", havingValue = "true", matchIfMissing = false)
    public LoggerExport loggerExport(MeterRegistry registry) {
        return new LoggerExport(registry, appName);
    }

    @Bean
    @ConditionalOnProperty(name = "other.monitor.metric.open", havingValue = "true", matchIfMissing = false)
    public CustomMeterRegistry customMeterRegistry(Clock clock, ApplicationContext applicationContext) {
        CustomRegistryConfig config = new CustomRegistryConfig(monitorConfig);
        return new CustomMeterRegistry(config, clock, collectorRegistry(), applicationContext);
    }

    @Bean
    @ConditionalOnProperty(name = "other.monitor.metric.open", havingValue = "true", matchIfMissing = false)
    public ResetMetersJob resetMetersJob(CollectorRegistry collectorRegistry) {
        return new ResetMetersJob(collectorRegistry, monitorConfig);
    }


}
