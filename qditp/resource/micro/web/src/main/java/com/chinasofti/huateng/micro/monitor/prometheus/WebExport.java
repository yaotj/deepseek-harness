package com.chinasofti.huateng.micro.monitor.prometheus;

import com.chinasofti.huateng.log4j2.CheckLoggerHealth;
import com.chinasofti.huateng.micro.monitor.configuration.MonitorConfig;
import com.chinasofti.huateng.micro.web.global.MoreInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.atomic.AtomicLong;

public class WebExport extends BaseExport {
    public static final Logger log = LoggerFactory.getLogger(WebExport.class);
    private final String appName;
    private AtomicLong webRunningMaxHolder;
    private static final String TIMEOUT_CNT_GAUGE = "web.mvc.timeout.cnt";
    private static final String RUNNING_CNT_GAUGE = "web.running";
    private static final String RUNNING_MAX_GAUGE = "web.running.max";
    private static final String LOGGING_TIME_GAUGE = "web.logging";

    @Autowired
    private CheckLoggerHealth checkLoggerHealth;

    private MonitorConfig monitorConfig;

    public WebExport(MeterRegistry registry, String appName, MonitorConfig monitorConfig) {
        super(registry);
        this.appName = appName;
        this.monitorConfig = monitorConfig;
        initGauge(TIMEOUT_CNT_GAUGE, "web mvc timeout cnt", "total", "name", appName);
        initGauge(RUNNING_CNT_GAUGE, "web running task cnt", "total", "name", appName);
        webRunningMaxHolder = initGauge(RUNNING_MAX_GAUGE, "web running task max cnt", "cnt", "name", appName);
        if (this.monitorConfig.isCheckLogWriter()) {
            initGauge(LOGGING_TIME_GAUGE, "logging take times of ms", "take", "name", appName);
        }
    }

    @Override
    public void publish() {
        long timeoutCnt = MoreInterceptor.timeoutCnt.get();
        updateGaugeValue(TIMEOUT_CNT_GAUGE, timeoutCnt);

        long currentConnectionsCnt = MoreInterceptor.runningCnt.get();
        updateGaugeValue(RUNNING_CNT_GAUGE, currentConnectionsCnt);

        long currentMax = webRunningMaxHolder.get();
        if (currentConnectionsCnt > currentMax) {
            webRunningMaxHolder.compareAndSet(currentMax, currentConnectionsCnt);
        }

        if (monitorConfig.isCheckLogWriter()) {
            updateGaugeValue(LOGGING_TIME_GAUGE, checkLoggerHealth.loggerTakeTimes());
        }
    }

    @Override
    protected void doClear() {
        webRunningMaxHolder.set(0);
        MoreInterceptor.timeoutCnt.set(0L);
    }
}
