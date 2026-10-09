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
 * <p>{@code @EnableRpcPaySign} 同时扫了 {@code rpc.paySign} 与 {@code rpc.alipay.paysign} 两个包，
 * 因此支付宝行程列表用的 {@code AlipayPaySignClient} 由它装配，**没有** {@code @EnableRpcAlipayPaySign} 这个注解。
 *
 * <p><b>刻意没有 {@code @EnableRpcTicket}</b>：支付宝行程列表与详情自 2026-09-18 起只用
 * {@code GATE_TXN_PAY} + {@code ALIPAY_PAY_TXN_DETAIL}，本模块已无 {@code TicketClient} 调用方，
 * {@code service.ticket.url} 也随之删除。要加回 MUST 先确认真有链路需要进出站明细。
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
