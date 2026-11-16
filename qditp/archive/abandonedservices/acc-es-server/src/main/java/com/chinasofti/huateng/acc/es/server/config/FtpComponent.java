package com.chinasofti.huateng.acc.es.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author rxwnc
 */
@Component
@ConfigurationProperties(prefix = "ftp")
@Data
public class FtpComponent {

    private String ip;

    private int port;

    private String username;

    private String password;

    private String reportTargetDir;

    private String reportLocalDir;

    private String excelDir;

    private String customLocalDir;

    private String customTargetDir;

}
