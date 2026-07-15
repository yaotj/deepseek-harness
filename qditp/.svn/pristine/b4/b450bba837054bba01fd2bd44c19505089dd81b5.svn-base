package com.chinasofti.huateng.log4j2;

import com.chinasofti.huateng.micro.web.utils.OperatingSystemDetector;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import java.text.MessageFormat;

public class CustomLoggingConfiguration implements ApplicationListener<ApplicationReadyEvent> {

    public static Logger log = LoggerFactory.getLogger(CustomLoggingConfiguration.class);

    @Value("${logging.config}")
    String xmlLocation;

    @Value("${other.logging.level}")
    String level;

    @Value("${other.logging.logPath}")
    String logPath;

    @Value("${other.logging.logBakDays}")
    String logBakDays;

    @Value("${other.logging.appName}")
    String appName;


    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!xmlLocation.isEmpty()) {
            return;
        }
        if (OperatingSystemDetector.isLinuxLog()) {
            String linuxFile = "classpath:log4j2-linux.xml";
            System.setProperty("webLoggerLevel", level);
            String moreLogPath = logPath + "/" + System.getenv().get("HOSTNAME");
            System.setProperty("logPath", moreLogPath);
            System.setProperty("logBakDays", logBakDays + "d");
            int logBakGzCnt = Integer.parseInt(logBakDays) * 24;
            System.setProperty("logBakGzCnt", String.valueOf(logBakGzCnt));
            System.setProperty("appName", appName);
            try {
                String log4j2Params = MessageFormat.format("Log4j2 configuration has been reloaded from {0},level={1},logPath={2},logBakDays={3},filename={4}.log", linuxFile, level, moreLogPath, logBakDays, appName);
                log.info(log4j2Params);
                LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
                Configurator.initialize(ctx.getName(), linuxFile);
                ctx.updateLoggers();
                log.info(log4j2Params);
            } catch (Exception ex) {
                String msg = MessageFormat.format("Log4j2 configuration reload err {0}", ex.getMessage());
                log.error(msg);
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        if (OperatingSystemDetector.isWindows()) {
            String msg = MessageFormat.format("windows tmp file {0}", logPath);
            log.info(msg);
        }
    }

}