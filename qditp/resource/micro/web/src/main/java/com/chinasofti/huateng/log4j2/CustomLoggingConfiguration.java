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
                // MUST 传 ClassLoader。Configurator.initialize(name, configLocation) 那个重载不传
                // ClassLoader，在 Spring Boot fat jar 下**解析不到 micro 嵌套 jar 里的自定义 appender**
                // （@Plugin(name="VictoriaLogs") 等）：配置本身能重载成功、RollingFile 也生效，但自定义
                // appender 静默缺失，表现为「已注入 VLOGS_URL 却零上报、连 vlogs-sender 线程都没有」。
                // 2026-09-08 在 para-server 实测复现（env 已注入，vlogs 线程数 0）。
                // 本类的 ClassLoader 即 LaunchedURLClassLoader，能同时看到 micro 嵌套 jar 与业务类。
                // **NEVER** 退回不带 ClassLoader 的重载——本文件 status="off"，log4j2 自己的
                // "Unable to locate plugin" 会被吞掉，退回后只能靠「上报为 0」反推，极难定位。
                Configurator.initialize(ctx.getName(), CustomLoggingConfiguration.class.getClassLoader(), linuxFile);
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