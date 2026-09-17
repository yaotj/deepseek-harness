package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import com.chinasofti.huateng.rpc.EnableRpcRecon;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.chinasofti.huateng.recon.storage.ReconLineBackfillProperties;
import com.chinasofti.huateng.recon.storage.ReconStorageProperties;
import com.chinasofti.huateng.recon.storage.ReconFtpProperties;
import com.chinasofti.huateng.recon.storage.ReconOrchestrationProperties;

/**
 * recon-server 启动类。
 *
 * <p>护栏：不显式声明 {@code @EnableScheduling} 不等于调度未启用 —— micro web 的
 * {@code WebAutoConfig} 已全局启用调度；NEVER 把「本模块调度未启用」写成可断言项。
 * 触发方与频率见 {@code docs/business/recon.md}。</p>
 */
@SpringBootApplication
@EnableDefaultMybatisAutoConfig
@EnableRpcRecon
@EnableConfigurationProperties({ReconStorageProperties.class, ReconFtpProperties.class, ReconOrchestrationProperties.class, ReconLineBackfillProperties.class})
public class ReconServer {
    public static void main(String[] args) {
        SpringApplication.run(ReconServer.class, args);
    }
}
