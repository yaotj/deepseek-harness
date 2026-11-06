package com.chinasofti.huateng.micro.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.text.MessageFormat;

public class DbPropertyFactory implements PropertySourceFactory {
    public static Logger log = LoggerFactory.getLogger(DbPropertyFactory.class);

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        if (!resource.getResource().exists()) {
            String fileName = resource.getResource().getFilename();
            String demo = new EncodedResource(new ClassPathResource("mysql.properties")).getContentAsString();
            String msg = MessageFormat.format("classpath file {0} is not exists,please make sure config other.sql.type={1} is correct. {0} content likes: {2}",
                    fileName, resource.getResource().getFilename().replace(".properties", ""), demo);
            log.error(msg);
            throw new IOException(msg);
        }
        return new ResourcePropertySource(resource);
    }


}
