package com.chinasofti.huateng.micro.monitor.configuration;

import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ZipkinOptimizeConfig {
    @Bean
    @ConditionalOnClass(name = {"io.opentelemetry.exporter.zipkin.ZipkinSpanExporter"})
    @ConditionalOnProperty(prefix = "management.zipkin.tracing", name = "endpoint", havingValue = "", matchIfMissing = false)
    @ConditionalOnMissingBean(BatchSpanProcessor.class)
    public BatchSpanProcessor zipkinBatchSpanProcessor(ObjectProvider<ZipkinSpanExporter> zipkinSpanExporterProvider) {
        ZipkinSpanExporter zipkinSpanExporter = zipkinSpanExporterProvider.getIfAvailable();
        if (zipkinSpanExporter == null) {
            return null;
        }

        return BatchSpanProcessor.builder(zipkinSpanExporter).setMaxQueueSize(10000)        // 内存队列上限，超过丢弃
                .setMaxExportBatchSize(512)    // 单次批量发送大小
                .setScheduleDelay(Duration.ofMillis(5000)) // 定时导出间隔
                .setExporterTimeout(Duration.ofSeconds(10)) // 导出超时
                .build();
    }
}
