package com.chinasofti.huateng.micro.web.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

public class ResourceUtils {

    public static Resource getLocalFileResource(String name) {
        if (name.startsWith("classpath:")) {
            return new ClassPathResource(name.substring("classpath:".length()));
        } else {
            return new FileSystemResource(name);
        }
    }

}
