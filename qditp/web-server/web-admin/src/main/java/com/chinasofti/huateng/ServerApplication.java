package com.chinasofti.huateng;

import com.chinasofti.huateng.rpc.EnableRpcF2f;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure;
import com.chinasofti.huateng.rpc.EnableRpcAccount;
import com.chinasofti.huateng.rpc.EnableRpcPaySign;

/**
 * 启动程序
 * 
 * @author zmzhang
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class })
@EnableRpcAccount
@EnableRpcF2f
@EnableRpcPaySign
public class ServerApplication
{
    public static void main(String[] args)
    {
        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(ServerApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  启动成功   ლ(´ڡ`ლ)ﾞ ");
    }
}
