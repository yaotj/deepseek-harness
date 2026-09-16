package com.chinasofti.huateng.gatetxnpay.paysign;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.gatetxnpay.constant.DebitStatus;
import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayRetCode;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.writer.GateTxnPayWriter;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.model.app.GatePayRequestDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 出站扣费的**唯一出账口**：组报文调 pay-sign，并把结果收敛成 `DEBIT_STATUS`。
 *
 * <p>三条链路共用本类，出账口只留一处是有意的：`requestPay`（出站首次，异步）、
 * `retryPay`（运营重试，同步）、离线码金额补偿（抢占成功后，同步）。
 * **NEVER** 在任何调用方再拼一份 {@link GatePayRequestDTO} —— 报文里
 * {@code TXN_DATE} 与金额两项各有一条踩过的坑（见下），复制一份就是复制两个缺陷。</p>
 *
 * <p>状态收敛只有一条规则：{@code retCode=0000} 才 PROCESSING，其余一切
 * （非 0000 / null / 抛异常）一律 RETRY 留给补偿。**NEVER** 把「没抛异常」
 * 当成扣款成功（AGENTS.md §5.2「返回 boolean 的 RPC 包装方法」同型陷阱）。</p>
 *
 * <p>本类 **NEVER** 加 {@code @Transactional}：内部就是一次 RPC，事务包住网络调用
 * 已经出过生产事故（AGENTS.md §5.2）。也 **NEVER** 注入 {@code GateTxnPayMapper} ——
 * 它只负责「发起 + 收敛」，扫表、算价、抢占都在调用方。</p>
 *
 * <p>出账口按 {@code ISSUE_CHANNEL_CODE} 分派两个渠道：{@code 07}（支付宝出行）调
 * alipay-pay-sign-server，其余一律调 pay-sign-server。两条分支的报文字段、金额单位、
 * 超时单位和签约流水号来源**都不一样**（支付宝的 {@code orderTimeOut} 是**分钟**、
 * {@code requestSignSeq} 取 {@code TICKET_TRANS_SEQ}、{@code industryDetail} 是甲方规定的
 * 21 键 JSON），**NEVER 把两套配置合并成一组**。状态收敛规则两边共用，因此
 * 支付宝分支的响应也被包成 {@link RequestPayResult} 交给同一个 {@code converge}。</p>
 */
@Component
public class PaySignInitiator {
    private static final Logger log = LoggerFactory.getLogger(PaySignInitiator.class);

    private final PaySignClient paySignClient;
    private final AlipayPaySignClient alipayPaySignClient;
    private final GateTxnPayWriter gateTxnPayWriter;
    /**
     * 两个渠道的报文组装已各自搬到一个工厂：本类因此从「14 个构造参数」降到 5 个、
     * 且**不再读任何 {@code @Value} 配置**。
     *
     * <p>拆的判据不是参数多，而是两套配置**单位与语义不同却名字相似**
     * （{@code orderTimeOut} 一边是秒、一边是分钟），混在同一串构造参数里时
     * 写错一个不报错、只在对端超时行为上表现出来。**NEVER 把它们合并回来。**</p>
     */
    private final GatePayRequestFactory gatePayRequestFactory;
    private final AlipayTripPayRequestFactory alipayTripPayRequestFactory;

    public PaySignInitiator(
            PaySignClient paySignClient,
            AlipayPaySignClient alipayPaySignClient,
            GateTxnPayWriter gateTxnPayWriter,
            GatePayRequestFactory gatePayRequestFactory,
            AlipayTripPayRequestFactory alipayTripPayRequestFactory) {
        this.paySignClient = paySignClient;
        this.alipayPaySignClient = alipayPaySignClient;
        this.gateTxnPayWriter = gateTxnPayWriter;
        this.gatePayRequestFactory = gatePayRequestFactory;
        this.alipayTripPayRequestFactory = alipayTripPayRequestFactory;
    }

    /**
     * 异步入口：出站首次扣款走这里，异常 **NEVER** 冲出去。
     *
     * <p>抛出去就成了「订单已入库、既没扣款也没留下 RETRY 标记」——补偿再也找不到它。
     * 只有连状态回写都失败时才重抛，让执行器把这条异常记进日志。</p>
     */
    public void initiateAsync(GateTxnPay order, GateTxnPayReqDTO request) {
        try {
            initiateAndConverge(order, request);
        } catch (Exception e) {
            log.error("异步调用pay-sign异常, orderNo={}", order.getOrderNo(), e);
            try {
                gateTxnPayWriter.updateOrderStatusFromPending(order, DebitStatus.RETRY.code(),
                        "异步调用pay-sign异常: " + e.getMessage());
            } catch (RuntimeException ex) {
                log.error("异步更新订单状态失败, orderNo={}", order.getOrderNo(), ex);
                throw ex;
            }
        }
    }

    /** 同步入口（出站首次与离线码补偿共用），返回收敛后的 {@code DEBIT_STATUS}。 */
    public String initiateAndConverge(GateTxnPay order, GateTxnPayReqDTO request) {
        return converge(order, request, "调用pay-sign失败");
    }

    /**
     * 运营重试入口：无入向 request，支付相关字段只能取订单快照。
     *
     * <p>与 {@link #initiateAndConverge} 只差「无响应」时的备注措辞——那句备注是运维
     * 区分「首次失败」与「重试失败」的唯一线索，**NEVER** 合并成同一句。</p>
     */
    public String retryAndConverge(GateTxnPay order) {
        return converge(order, null, "重试调用pay-sign失败");
    }

    private String converge(GateTxnPay order, GateTxnPayReqDTO request, String noResponseReason) {
        RequestPayResult payResult = requestPay(order, request);
        String nextStatus = isSuccess(payResult) ? DebitStatus.PROCESSING.code() : DebitStatus.RETRY.code();
        String reason = payResult == null ? noResponseReason : payResult.getRetMsg();
        gateTxnPayWriter.updateOrderStatusFromPending(order, nextStatus, reason);
        return nextStatus;
    }

    /**
     * 渠道分派：{@code ISSUE_CHANNEL_CODE=07} 走支付宝出行，其余走支付中心（pay-sign）。
     *
     * <p>判据取**订单上的**发行渠道而不是入向 request —— {@code retryPay} 与离线补偿都没有
     * request，只能靠订单快照；两条路必须选出同一个渠道，否则重试会打到另一家。</p>
     */
    private RequestPayResult requestPay(GateTxnPay order, GateTxnPayReqDTO request) {
        if (IssueChannelCodeEnum.isAlipay(order.getIssueChannelCode())) {
            return requestAlipayTripPay(order);
        }
        return requestPaySign(order, request);
    }

    /**
     * 调用 pay-sign 发起免密扣款。报文组装在 {@link GatePayRequestFactory}。
     */
    private RequestPayResult requestPaySign(GateTxnPay order, GateTxnPayReqDTO request) {
        GatePayRequestDTO payRequest = gatePayRequestFactory.build(order, request);
        log.info("调用pay-sign请求支付, 入参 orderNo={}, cardId={}, amount={}, paymentVendor={}, requestSignSeq={}, txnDate={}, discountFee={}, discountInfo={}",
                order.getOrderNo(), order.getCardId(), order.getTotalAmount(),
                payRequest.getPaymentVendor(), payRequest.getRequestSignSeq(),
                payRequest.getTxnDate(), payRequest.getDiscountFee(), payRequest.getDiscountInfo());
        RequestPayResult response = paySignClient.requestPay(payRequest);
        log.info("调用pay-sign请求支付, 返回={}", JSON.toJSONString(response));
        return response;
    }

    /**
     * 支付宝出行免密扣费。报文组装在 {@link AlipayTripPayRequestFactory}。
     *
     * <p>响应被包成 {@link RequestPayResult} 交给同一个 {@link #converge} —— 两条渠道的
     * 报文互不相同，但「0000 才 PROCESSING」这条状态收敛规则是共用的。</p>
     */
    private RequestPayResult requestAlipayTripPay(GateTxnPay order) {
        AlipayTripRequestPayReqDTO payRequest = alipayTripPayRequestFactory.build(order);
        log.info("调用alipay-pay-sign请求支付, 入参 orderNo={}, cardId={}, amount={}, requestSignSeq={}, txnDate={}, industryDetail={}",
                order.getOrderNo(), order.getCardId(), order.getTotalAmount(),
                payRequest.getRequestSignSeq(), order.getTxnDate(), payRequest.getIndustryDetail());
        AlipayTripRequestPayRespDTO response = alipayPaySignClient.alipayTripRequestPay(payRequest);
        log.info("调用alipay-pay-sign请求支付, 返回={}", JSON.toJSONString(response));
        if (response == null) {
            return null;
        }
        RequestPayResult result = new RequestPayResult();
        result.setRetCode(response.getRetCode());
        result.setRetMsg(response.getRetMsg());
        return result;
    }

    private boolean isSuccess(RequestPayResult result) {
        return result != null && GateTxnPayRetCode.SUCCESS.equals(result.getRetCode());
    }
}
