package com.chinasofti.huateng.micro.monitor.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "other.monitor")
public class MonitorConfig {

    long pushOfSeconds = 30L;

    String resetAllMetersCron = "0 0 5 * * ?";

    boolean printToLogger = false;

    boolean checkLogWriter = false;

    public boolean isCheckLogWriter() {
        return checkLogWriter;
    }

    public void setCheckLogWriter(boolean checkLogWriter) {
        this.checkLogWriter = checkLogWriter;
    }

    public String getResetAllMetersCron() {
        return resetAllMetersCron;
    }

    public void setResetAllMetersCron(String resetAllMetersCron) {
        this.resetAllMetersCron = resetAllMetersCron;
    }

    public long getPushOfSeconds() {
        return pushOfSeconds;
    }

    public void setPushOfSeconds(long pushOfSeconds) {
        this.pushOfSeconds = pushOfSeconds;
    }

    public boolean isPrintToLogger() {
        return printToLogger;
    }

    public void setPrintToLogger(boolean printToLogger) {
        this.printToLogger = printToLogger;
    }
}
