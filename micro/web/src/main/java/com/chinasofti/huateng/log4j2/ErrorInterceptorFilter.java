package com.chinasofti.huateng.log4j2;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.Message;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Plugin(name = "ErrorInterceptorFilter", category = "Core", elementType = "filter", printObject = true)
public final class ErrorInterceptorFilter extends org.apache.logging.log4j.core.filter.AbstractFilter {

    private final Layout<?> layout;

    public ErrorInterceptorFilter(Layout<?> layout) {
        this.layout = layout != null ? layout : PatternLayout.createDefaultLayout();
    }

    @PluginFactory
    public static ErrorInterceptorFilter createFilter(@PluginElement("Layout") Layout<?> layout) {
        return new ErrorInterceptorFilter(layout);
    }

    public static boolean openLogEvent = false;
    public static final BlockingQueue<String> LOG_QUEUE = new ArrayBlockingQueue<>(10000);

    public static AtomicLong errorCnt = new AtomicLong();

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        if (level == Level.ERROR) {
            errorCnt.incrementAndGet();
        }
        return Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        if (level == Level.ERROR) {
            errorCnt.incrementAndGet();
        }
        return Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        if (level == Level.ERROR) {
            errorCnt.incrementAndGet();
        }
        return Result.NEUTRAL;
    }

    @Override
    public Result filter(LogEvent event) {
        if (event.getLevel() == Level.ERROR) {
            errorCnt.incrementAndGet();
        }
        if (openLogEvent) {
            LOG_QUEUE.offer(new String(layout.toByteArray(event)));
        }
        return Result.NEUTRAL;
    }


}
