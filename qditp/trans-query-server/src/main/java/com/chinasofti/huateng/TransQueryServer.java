package com.chinasofti.huateng;

import com.chinasofti.huateng.rpc.EnableRpcDailyTicket;
import com.chinasofti.huateng.rpc.EnableRpcGateTxnPay;
import com.chinasofti.huateng.rpc.EnableRpcPara;
import com.chinasofti.huateng.rpc.EnableRpcPaySign;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 交易查询服务（纯转发壳）。
 *
 * <p>统一承接「乘车 / 交易记录查询」这一类只读接口，模式固定为
 * <b>先查 gate-txn-pay 拿基础订单，再批量到对应服务补充详情</b>：
 * <ul>
 *   <li>IF8A-05 交易列表 —— {@code gateTxnPayClient.requestTransList} + {@code paySignClient.queryPayTxnBatch} 批量补支付状态</li>
 *   <li>IF8A-41 交易统计 —— 直接转发 gate-txn-pay，卡类型聚合桶映射（{@code CardTypeMapping}）在本服务完成</li>
 *   <li>IF8A-34 交易详情 —— 转发 gate-txn-pay + para 站名解析，并做订单归属校验</li>
 * </ul>
 *
 * <p><b>本服务没有数据源，NEVER 加 {@code @EnableDefaultMybatisAutoConfig} 或任何 mapper。</b>
 * pom 里也没有 ojdbc / sql-datasource / mybatis-adaptor（理由见 pom 内注释）。要查库一律经 rpc
 * 问持有该表的服务，否则同一张表会被两个服务读、owner 归属被破坏。
 *
 * <p><b>TODO（支付宝 / 日票乘车记录查询迁入本服务）</b>
 * <ul>
 *   <li><b>支付宝出行行程列表 / 详情</b>（{@code /ci/channel/findTravelList} /
 *       {@code findTravelDetail}）**目前仍在 ticket-server**，未迁。阻塞原因：那两个接口的
 *       {@code AlipayTripHandler} 直读 {@code QRCODE_TXN_DETAIL}（4 个 mapper 方法），
 *       与本服务「无库 + 纯转发」的定位冲突；把 mapper 搬过来等于让本服务直读乘车码域的表。
 *       <b>迁入前提：支付宝 pay-sign 侧新表建好</b>，届时数据源改为查支付宝库、
 *       不再回落 ticket-server，再把这两个端点搬进来。<b>NEVER 为了「先迁过来」而在本模块
 *       引入 mapper 或 ojdbc 依赖。</b></li>
 *   <li><b>日票乘车记录查询</b>同理，待接入后按同一模式（gate-txn-pay 拿基础订单 +
 *       批量到 daily-ticket-server 补详情）实现。</li>
 *   <li>支付宝那一项迁入时才需要 {@code @EnableRpcTicket} 与 {@code service.ticket.url}；
 *       **现在没有读取方，因此不配**（避免留下无人读的配置键误导排查）。
 *       {@code @EnableRpcDailyTicket} 与 {@code service.dailyTicket.url} **已于 2026-09-15 补齐**：
 *       IF8A-34 详情要给日票免扣费单填三个支付字段（{@code TransDetailQueryHandler
 *       .enrichDailyTicketPayInfo} → {@code /ci/daily-ticket/queryDailyTicketPayInfo}），
 *       已经有真实读取方，**NEVER 再按「日票未迁入」把这两处删掉**。</li>
 * </ul>
 */
@SpringBootApplication
@EnableRpcGateTxnPay
@EnableRpcPaySign
@EnableRpcPara
@EnableRpcDailyTicket
public class TransQueryServer {
    public static void main(String[] args) {
        SpringApplication.run(TransQueryServer.class, args);
    }
}
