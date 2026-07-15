package com.chinasofti.huateng.web.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.chinasofti.huateng.common.config.PointsConfig;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * OpenAPI接口配置
 * 
 * @author zmzhang
 */
@Configuration
@ConditionalOnProperty(name = "swagger.enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerConfig
{
    /** 系统基础配置 */
    @Autowired
    private PointsConfig pointsConfig;

    /** 设置请求的统一前缀 */
    @Value("${swagger.pathMapping}")
    private String pathMapping;

    /**
     * 创建API
     */
    @Bean
    public OpenAPI createRestApi()
    {
        return new OpenAPI()
                .addServersItem(new Server().url(pathMapping))
                .info(apiInfo())
                .schemaRequirement("Authorization", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .name("Authorization")
                        .in(SecurityScheme.In.HEADER))
                .addSecurityItem(new SecurityRequirement().addList("Authorization"));
    }

    /**
     * 添加摘要信息
     */
    private Info apiInfo()
    {
        return new Info()
                .title("标题：若依管理系统_接口文档")
                .description("描述：用于管理集团旗下公司的人员信息,具体包括XXX,XXX模块...")
                .contact(new Contact().name(pointsConfig.getName()))
                .version("版本号:" + pointsConfig.getVersion());
    }
}
