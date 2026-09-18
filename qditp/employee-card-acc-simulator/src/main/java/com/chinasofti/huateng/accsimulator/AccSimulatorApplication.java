package com.chinasofti.huateng.accsimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDefaultMybatisAutoConfig
public class AccSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccSimulatorApplication.class, args);
    }
}
