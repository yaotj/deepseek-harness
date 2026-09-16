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
 * <p><b>NEVER 加回 {@code @EnableScheduling}。</b>用户 2026-09-11 明确要求「不使用 EnableScheduling，
 * 改用 web-admin 调用，改为每日执行一次，由 web-admin 控制频率」，因此本模块**一个 {@code @Scheduled} 都没有**，
 * 日终对账的触发时机与频率全部由 web-admin 的 {@code sys_job}（{@code reconQuartzTask.runDailyBatch()}）决定，
 * 入口是 {@code POST /internal/recon/daily/run}。加回本注解等于让 {@code sys_job} 与本模块形成两套互不知情的
 * 调度源，改 cron 时只改一处就会出现「以为改了、实际另一套还在按老频率跑」，并造成重复建批次与重复投递
 * （与 {@code docs/architecture/web-server.md} §7.1 末条同一约束）。</p>
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
