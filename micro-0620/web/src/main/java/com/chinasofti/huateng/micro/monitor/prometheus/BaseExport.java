package com.chinasofti.huateng.micro.monitor.prometheus;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class BaseExport {
    private static final Logger log = LoggerFactory.getLogger(BaseExport.class);
    private final MeterRegistry registry;
    private final Map<String, Meter> meters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gaugeValueMap = new ConcurrentHashMap<>();

    private static final int MAX_METER_THRESHOLD = 10240;

    public BaseExport(MeterRegistry registry) {
        this.registry = registry;
    }

    public abstract void publish();

    public void checkMeterCountAndCleanIfNeeded() {
        try {
            int meterCount = registry.getMeters().size();
            if (meterCount > MAX_METER_THRESHOLD) {
                clear();
            }
        } catch (Exception e) {
            log.warn("检查Meter数量失败", e);
        }
    }

    public void clear() {
        try {
            log.info("开始清理,当前注册Meter总数: {}", registry.getMeters().size());
            if (meters.size() > 20) {
                log.info("清理自定义Meter数:{}", meters.size());
                meters.values().forEach(registry::remove);
                meters.clear();
                gaugeValueMap.clear();
            }

            if (registry.getMeters().size() > MAX_METER_THRESHOLD) {
                for (Meter m : registry.getMeters()) {
                    if (m.getId().getName().startsWith("custom")) {
                        registry.remove(m);
                    }
                }
            }

            if (registry.getMeters().size() > MAX_METER_THRESHOLD) {
                registry.clear();
                log.info("清理全部Meter");
            }

            doClear();
            log.info("已清理,剩余Meter数:{}", registry.getMeters().size());
        } catch (Exception e) {
            log.warn("清理Meter失败", e);
        }
    }

    protected abstract void doClear();

    protected AtomicLong initGauge(String group, String description, String baseUnit, String... tags) {
        return gaugeValueMap.computeIfAbsent(group, k -> {
            AtomicLong valueHolder = new AtomicLong(0);
            List<Tag> tagList = buildTags(tags);

            Gauge gauge = Gauge.builder(group, valueHolder, AtomicLong::get).tags(tagList).description(description).baseUnit(baseUnit).register(registry);

            meters.put(group, gauge);
            return valueHolder;
        });
    }

    protected void updateGaugeValue(String group, long value) {
        AtomicLong v = gaugeValueMap.get(group);
        if (v != null) v.set(value);
    }

    private List<Tag> buildTags(String... tags) {
        if (tags == null || tags.length % 2 != 0) return List.of();
        return Stream.iterate(0, i -> i + 2).limit(tags.length / 2).map(i -> Tag.of(tags[i], tags[i + 1])).collect(Collectors.toList());
    }
}