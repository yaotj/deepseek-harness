package com.chinasofti.huateng.micro.monitor.prometheus;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CustomMeterRegistry extends StepMeterRegistry {
    public static final Logger log = LoggerFactory.getLogger(CustomMeterRegistry.class);

    private final Map<String, BaseExport> baseExports = new ConcurrentHashMap<>();
    private final ReentrantLock cleanLock = new ReentrantLock();

    private final CollectorRegistry collectorRegistry;
    private final ApplicationContext applicationContext;

    public CustomMeterRegistry(CustomRegistryConfig config, Clock clock,
                               CollectorRegistry collectorRegistry,
                               ApplicationContext applicationContext) {
        super(config, clock);
        this.collectorRegistry = collectorRegistry;
        this.applicationContext = applicationContext;
        start();
    }

    @Override
    protected void publish() {
        if (baseExports.isEmpty() && applicationContext != null) {
            cleanLock.lock();
            try {
                if (baseExports.isEmpty()) {
                    baseExports.putAll(applicationContext.getBeansOfType(BaseExport.class));
                    log.info("Initialized {} BaseExport beans", baseExports.size());
                }
            } catch (Exception e) {
                log.error("Failed to load BaseExport beans", e);
            } finally {
                cleanLock.unlock();
            }
        }

        baseExports.forEach((beanName, export) -> {
            try {
                export.checkMeterCountAndCleanIfNeeded();
                export.publish();
            } catch (Exception e) {
                log.error("Failed to publish metrics for BaseExport [{}]", beanName, e);
            }
        });
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    public void close() {
        cleanLock.lock();
        try {
            baseExports.forEach((name, export) -> {
                try {
                    export.clear();
                } catch (Exception e) {
                    log.error("clear error: {}", name, e);
                }
            });
            baseExports.clear();

            if (collectorRegistry != null) {
                collectorRegistry.clear();
            }

            getMeters().forEach(this::remove);
            log.info("CustomMeterRegistry 已关闭，内存全部回收");
        } finally {
            cleanLock.unlock();
        }
    }
}