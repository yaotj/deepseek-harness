package com.chinasofti.huateng.micro.monitor.prometheus;

import com.chinasofti.huateng.micro.monitor.configuration.MonitorConfig;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.time.Duration;

public class CustomRegistryConfig implements StepRegistryConfig {

    MonitorConfig monitorConfig;

    public CustomRegistryConfig(MonitorConfig monitorConfig) {
        this.monitorConfig = monitorConfig;
    }

    @Override
    public String prefix() {
        return "custom";
    }

    @Override
    public String get(String s) {
        return null;
    }

    @Override
    public Duration step() {
        return Duration.ofSeconds(monitorConfig.getPushOfSeconds());
    }
}
