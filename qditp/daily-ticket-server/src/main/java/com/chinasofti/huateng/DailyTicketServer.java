package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import com.chinasofti.huateng.rpc.EnableRpcRecon;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 日票服务启动类。
 *
 * <p><b>本服务不依赖 card-pool-server，NEVER 加 {@code @EnableRpcCardPool}</b>：日票实例的
 * {@code CARD_NUM} 直接取 APP 激活请求上送的 {@code cardNum}，即开户（{@code businessType=ACCOUNT_OPEN}）
 * 时从卡池预占的那张卡号。原因见 {@code DailyTicketServiceImpl#updateTicket} 的方法注释：
 * 激活时再向卡池预占一张，会因 {@code UK_LOGIC_CARD_POOL_BUSINESS} 唯一约束必然拿到另一个卡号，
 * 与 APP 侧持有并上送闸机的开户卡号不一致，导致 {@code selectForEntryCheck} / {@code markUsed}
 * 按 {@code CARD_NUM} 精确匹配恒命中 0 行（2026-09-10 线上事故）。
 * 若后续 ACC 要求日票持独立卡号，MUST 先设计「开户卡号 ↔ 日票卡号」映射表并同步改 APP 取码链路。</p>
 *
 * <p>{@code @EnableRpcRecon} 引入日终对账的分片上送客户端（{@code ReconPartUploader} /
 * {@code ReconPartSink}），供 {@code ReconExportService} 导出 DETAIL 与 PAY 两类文件使用。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties
@EnableDefaultMybatisAutoConfig
@EnableRpcRecon
public class DailyTicketServer {
    public static void main(String[] args) {
        SpringApplication.run(DailyTicketServer.class, args);
    }
}
