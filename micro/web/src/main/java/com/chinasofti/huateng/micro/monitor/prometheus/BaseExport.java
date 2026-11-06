package com.chinasofti.huateng.micro.monitor.prometheus;

import com.chinasofti.huateng.micro.web.utils.SpringContextUtils;
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
            log.info("当前注册Meter总数: {}", registry.getMeters().size());
            log.info("{}自定义Meter总数: {}", this.getClass().getSimpleName(), meters.size());
            if (meters.size() > 20) {
                log.info("清理自定义Meter数:{}", meters.size());
                meters.values().forEach(registry::remove);
                meters.clear();
                gaugeValueMap.clear();
            }

            if (registry.getMeters().size() > MAX_METER_THRESHOLD) {
                for (Meter m : registry.getMeters()) {
                    if (m.getId().getName().startsWith("custom")) {
                        log.info("清理游离Meter:{}", m.getId().getName());
                        registry.remove(m);
                    }
                }
            }

            if (registry.getMeters().size() > MAX_METER_THRESHOLD) {
                log.info("自动创建的Meter数量过多,强制清理全部Meter");
                registry.clear();
                Map<String, BaseExport> all = SpringContextUtils.getApplicationContext().getBeansOfType(BaseExport.class);
                if (all.size() > 0) {
                    for (Map.Entry<String, BaseExport> entry : all.entrySet()) {
                        BaseExport export = entry.getValue();
                        export.meters.clear();
                        export.gaugeValueMap.clear();
                    }
                }
            }

            doClear();
            log.info("剩余Meter数:{}", registry.getMeters().size());
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

    protected boolean contain(String group) {
        AtomicLong v = gaugeValueMap.get(group);
        if (v != null) {
            return true;
        } else {
            return false;
        }
    }

    protected boolean updateGaugeValue(String group, long value) {
        AtomicLong v = gaugeValueMap.get(group);
        if (v != null) {
            v.set(value);
            return true;
        } else {
            return false;
        }
    }

    private List<Tag> buildTags(String... tags) {
        if (tags == null || tags.length % 2 != 0) return List.of();
        return Stream.iterate(0, i -> i + 2).limit(tags.length / 2).map(i -> Tag.of(tags[i], tags[i + 1])).collect(Collectors.toList());
    }
}