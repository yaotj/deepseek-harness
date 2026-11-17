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
import com.chinasofti.huateng.recon.storage.ThirdPartyReconProperties;


@SpringBootApplication
@EnableDefaultMybatisAutoConfig
@EnableRpcRecon
@EnableConfigurationProperties({ReconStorageProperties.class, ReconFtpProperties.class, ReconOrchestrationProperties.class, ReconLineBackfillProperties.class, ThirdPartyReconProperties.class})
public class ReconServer {
    public static void main(String[] args) {
        SpringApplication.run(ReconServer.class, args);
    }
}
