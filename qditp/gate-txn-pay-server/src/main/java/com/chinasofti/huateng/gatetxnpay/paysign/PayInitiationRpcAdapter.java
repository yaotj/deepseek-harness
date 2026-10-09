package com.chinasofti.huateng.gatetxnpay.paysign;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.model.app.GatePayRequestDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.paysign.RegisterCompletedPayTxnReqDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 两个支付渠道的发起调用适配器：裸 DTO 应答 → {@link RpcOutcome} 三态翻译的**唯一**落点。
 *
 * <p>为什么在本模块新起一个适配器、而不是给 {@code rpc} 模块的 {@code PaySignClient.requestPay} /
 * {@code AlipayPaySignClient.alipayTripRequestPay} 改签名：那两个方法的返回类型分别是
 * {@code RequestPayResult} 与 {@code AlipayTripRequestPayRespDTO}，调用方不止本模块，而 {@code rpc}
 * 的版本号锁死（AGENTS.md §7）只能增方法不能改签名。形态照 {@code pay-sign-server} 与
 * {@code alipay-pay-sign-server} 各自的 {@code port/DebitSyncRpcAdapter} —— 本项目已有的
 * 「业务模块内部把裸 DTO 翻成 {@code RpcOutcome}」样板就是它们。
 *
 * <p><b>三态的判据，改这个类前 MUST 读懂</b>：
 * <ul>
 *   <li>{@code Ok} —— 对端答了 {@code 0000}（{@link RpcOutcome#SUCCESS_CODE} 与
 *       {@code GateTxnPayRetCode.SUCCESS} 同为 {@code "0000"}，翻译直接走
 *       {@link RpcOutcome#ofRetCode}）；</li>
 *   <li>{@code BizRejected} —— <b>对端答了，但不是 0000</b>。这类重推一万次也不会成功，
 *       调用方 MUST 一次即终态，**NEVER 把它和下面那条合并成「失败」**；</li>
 *   <li>{@code Unreachable} —— <b>压根没拿到业务答复</b>（连不上 / 超时 / 反序列化炸）。只有这类才该进补偿重试。</li>
 * </ul>
 *
 * <p><b>响应体为 {@code null} 归 {@code BizRejected}、NEVER 归 {@code Unreachable}</b>：
 * {@code ProxyWebClient} 在传输层出问题时会抛异常（那条走 catch），能走到「返回 null」说明 HTTP 已通、
 * 是应答体本身空或解析不出内容 —— 那是契约问题，重试解决不了。与 {@code DebitSyncRpcAdapter} 同口径。
 *
 * <p><b>本类 NEVER 抛异常</b>：它是「把异常翻译成可判定结果」的那一层，一旦向上抛就把
 * {@code Unreachable} 与 {@code BizRejected} 的区分又丢回去了。
 */
@Component
public class PayInitiationRpcAdapter {

    private static final Logger log = LoggerFactory.getLogger(PayInitiationRpcAdapter.class);

    private final PaySignClient paySignClient;
    private final AlipayPaySignClient alipayPaySignClient;

    public PayInitiationRpcAdapter(PaySignClient paySignClient, AlipayPaySignClient alipayPaySignClient) {
        this.paySignClient = paySignClient;
        this.alipayPaySignClient = alipayPaySignClient;
    }

    /** 调支付中心通道（pay-sign）发起免密扣款。 */
    public RpcOutcome requestPaySign(GatePayRequestDTO payRequest) {
        try {
            RequestPayResult response = paySignClient.requestPay(payRequest);
            if (response == null) {
                log.error("调用pay-sign请求支付：应答为空, orderNo={}", payRequest.getOrderNo());
                return new RpcOutcome.BizRejected(null, "pay-sign 应答为空");
            }
            log.info("调用pay-sign请求支付, 返回={}", JSON.toJSONString(response));
            return RpcOutcome.ofRetCode(response.getRetCode(), response.getRetMsg());
        } catch (Exception e) {
            log.error("调用pay-sign请求支付未获业务答复（可重试）, orderNo={}", payRequest.getOrderNo(), e);
            return new RpcOutcome.Unreachable(e);
        }
    }

    /** 调支付宝出行通道（alipay-pay-sign）发起免密扣费。 */
    public RpcOutcome requestAlipayTripPay(AlipayTripRequestPayReqDTO payRequest) {
        try {
            AlipayTripRequestPayRespDTO response = alipayPaySignClient.alipayTripRequestPay(payRequest);
            if (response == null) {
                log.error("调用alipay-pay-sign请求支付：应答为空, orderNo={}", payRequest.getOrderNo());
                return new RpcOutcome.BizRejected(null, "alipay-pay-sign 应答为空");
            }
            log.info("调用alipay-pay-sign请求支付, 返回={}", JSON.toJSONString(response));
            return RpcOutcome.ofRetCode(response.getRetCode(), response.getRetMsg());
        } catch (Exception e) {
            log.error("调用alipay-pay-sign请求支付未获业务答复（可重试）, orderNo={}", payRequest.getOrderNo(), e);
            return new RpcOutcome.Unreachable(e);
        }
    }

    /**
     * 调 pay-sign 登记一条「已完成、不出网」的支付流水（BOM 补站等现场已收款的订单）。
     *
     * <p>与上面两个方法同属本类，是因为**出网目标与三态翻译口径完全一致**，差别只在对端那一侧：
     * 它不发起扣款、只补 {@code PAY_TXN_DETAIL} 那一行（用户 2026-09-22 裁决「没有流水行就不是完整订单」）。
     * 对端已按 {@code UK_PAY_TXN_DETAIL_ORDER} 幂等，本方法**可安全重试**。
     *
     * <p><b>NEVER 把它合进 {@code requestPaySign} 当一个分支</b>：那个方法一定会触发真实扣款，
     * 两者的钱流向相反（一个从乘客账上扣、一个只是记账），混在一起的代价是重复收费（ADR-D136）。
     *
     * <p>{@code PaySignClient.registerCompletedTxn} 本身已返 {@link RpcOutcome} 且内部不抛异常，
     * 这里的 catch 只是**双保险**（照本类「NEVER 抛异常」的约定），不是重复处理。
     */
    public RpcOutcome registerCompletedTxn(RegisterCompletedPayTxnReqDTO request) {
        try {
            RpcOutcome outcome = paySignClient.registerCompletedTxn(request);
            log.info("调用pay-sign登记已完成支付流水, orderNo={}, outcome={}", request.getOrderNo(), outcome);
            return outcome;
        } catch (Exception e) {
            log.error("调用pay-sign登记已完成支付流水未获业务答复（可重试）, orderNo={}", request.getOrderNo(), e);
            return new RpcOutcome.Unreachable(e);
        }
    }
}
