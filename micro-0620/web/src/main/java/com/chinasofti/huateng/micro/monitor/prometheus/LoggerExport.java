package com.chinasofti.huateng.micro.monitor.prometheus;

import com.chinasofti.huateng.log4j2.ErrorInterceptorFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerExport extends BaseExport {
    public static final Logger log = LoggerFactory.getLogger(LoggerExport.class);
    private final String appName;
    private static final String ERROR_CNT_GAUGE = "log.error.cnt";

    public LoggerExport(MeterRegistry registry, String appName) {
        super(registry);
        this.appName = appName;
        initGauge(ERROR_CNT_GAUGE, "logger error cnt", "total", "name", appName);
    }

    @Override
    public void publish() {
        long errorCnt = ErrorInterceptorFilter.errorCnt.get();
        updateGaugeValue(ERROR_CNT_GAUGE, errorCnt);
    }

    @Override
    protected void doClear() {
        ErrorInterceptorFilter.errorCnt.set(0L);
    }
}