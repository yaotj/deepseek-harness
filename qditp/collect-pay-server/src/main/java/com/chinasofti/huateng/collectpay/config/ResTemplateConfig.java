package com.chinasofti.huateng.collectpay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Created by tian on 2022/3/15.
 */

@Configuration
public class ResTemplateConfig {

    @Bean(name = "httpRestTemplate")
    public RestTemplate httpRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }


}