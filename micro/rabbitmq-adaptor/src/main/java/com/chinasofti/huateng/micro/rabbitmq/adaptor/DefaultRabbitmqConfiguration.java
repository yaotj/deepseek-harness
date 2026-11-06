package com.chinasofti.huateng.micro.rabbitmq.adaptor;

import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:mq.properties")
@ServletComponentScan("com.chinasofti.huateng.micro.rabbitmq.adaptor")
public class DefaultRabbitmqConfiguration {

    @Bean
    public SimpleProducer simpleProducer() {
        return new SimpleProducer();
    }
}
