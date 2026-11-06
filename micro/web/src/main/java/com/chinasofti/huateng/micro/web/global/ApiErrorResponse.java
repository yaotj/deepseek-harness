package com.chinasofti.huateng.micro.web.global;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ApiErrorResponse extends ASimpleResultVo {

    public static Logger log = LoggerFactory.getLogger(ApiErrorResponse.class);

    public ApiErrorResponse(String url, Exception ex) {
        super.setCode(UUID.randomUUID().toString());
        if (ex.getMessage().length() > 20) {
            log.error("{} {}",url, ex.getMessage());
        } else {
            log.error(url, ex);
        }

    }


}
