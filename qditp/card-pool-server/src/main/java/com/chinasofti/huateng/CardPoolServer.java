package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 逻辑卡号池服务启动类。
 *
 * <p>本模块不注册 {@code @EnableScheduling}：卡池维护由外部调度经
 * {@code POST /internal/card-pools/maintenance} 触发，避免多副本重复执行。</p>
 *
 * <p>IF7B-01 由本模块的 {@code AccLogicNumClient} 直连 ACC，不经 {@code acc-secure-server}
 * 转发，因此不需要任何 {@code @EnableRpcXxx}。</p>
 */
@SpringBootApplication
@EnableDefaultMybatisAutoConfig
public class CardPoolServer {

    /**
     * 启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CardPoolServer.class, args);
    }
}
