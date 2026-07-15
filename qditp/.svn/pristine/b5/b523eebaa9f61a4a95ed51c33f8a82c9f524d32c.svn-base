package com.chinasofti.huateng.micro.monitor.prometheus;


import com.chinasofti.huateng.micro.monitor.configuration.MonitorConfig;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

public class ResetMetersJob {
    public static final Logger log = LoggerFactory.getLogger(ResetMetersJob.class);

    private CollectorRegistry collectorRegistry;

    private MonitorConfig monitorConfig;

    @Autowired
    private CustomMeterRegistry customMeterRegistry;

    @Autowired
    private Map<String, BaseExport> baseExports;

    public ResetMetersJob(CollectorRegistry collectorRegistry, MonitorConfig monitorConfig) {
        this.collectorRegistry = collectorRegistry;
        this.monitorConfig = monitorConfig;
    }

    @Scheduled(cron = "${other.monitor.resetAllMetersCron}")
    @Async("taskScheduler")
    public void resetAllMeters() {
        log.info("Start resetting custom meters data");
        try {
            baseExports.forEach((k, v) -> {
                try {
                    v.clear();
                } catch (Exception e) {
                    log.error("Failed to clear meter for {}", k, e);
                }
            });
            customMeterRegistry.close();
            collectorRegistry.clear();
            log.info("Reset custom meters data successfully");
        } catch (Exception e) {
            log.error("Failed to reset custom meters data", e);
        }
    }

    @Scheduled(cron = "${other.monitor.logAllMetersCron}")
    @Async("taskScheduler")
    public void logAllMetric() {
        if (monitorConfig.isPrintToLogger()) {
            try (Writer writer = new StringWriter()) {
                TextFormat.write004(writer, this.collectorRegistry.metricFamilySamples());
                String msg = writer.toString().replaceAll("\n", " ");
                log.info("Custom metrics: {}", msg);
            } catch (Exception e) {
                log.error("Logging metrics failed", e);
            }
        }
    }
}