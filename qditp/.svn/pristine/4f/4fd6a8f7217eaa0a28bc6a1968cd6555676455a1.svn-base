package com.chinasofti.huateng.micro.web;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.io.Writer;

@Component
public class ShutdownHookPrinter implements ApplicationListener<ContextClosedEvent> {

    public static Logger log = LoggerFactory.getLogger(ShutdownHookPrinter.class);

    @Autowired
    CollectorRegistry collectorRegistry;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        try {
            Writer writer = new StringWriter();
            TextFormat.write004(writer, this.collectorRegistry.metricFamilySamples());
            String msg = writer.toString();
            msg = msg.replaceAll("\n", "--");
            log.info(msg);
        } catch (Exception var6) {
            log.error("logging metrics failed");
        }
    }
}
