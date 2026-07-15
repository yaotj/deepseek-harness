package com.chinasofti.huateng.log4j2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckLoggerHealth {
    public static Logger log = LoggerFactory.getLogger(CheckLoggerHealth.class);

    public long loggerTakeTimes() {
        long start = System.currentTimeMillis();
        log.info("CheckLoggerHealth start");
        log.info("CheckLoggerHealth end");
        long end = System.currentTimeMillis();
        return end - start;
    }
}
