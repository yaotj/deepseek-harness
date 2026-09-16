package com.chinasofti.huateng.facepay.channel.paycenter;

import com.alibaba.fastjson2.JSON;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付中心报文组装。<b>纯 Java 类，不带 Spring 注解</b>，构造函数注入配置与签名器，可直接单测。
 *
 * <h2>为什么用 LinkedHashMap 而不是 DTO</h2>
 * bizData 是「先 JSON 再 Base64 再整体参与签名」，因此<b>键的顺序会进入待签串</b>。用
 * {@code LinkedHashMap} 显式写死顺序，与旧 {@code PayCenterCommon} 的三个 builder 逐字对齐；
 * 若改用 POJO，键序取决于 Fastjson 的字段排序策略，升级依赖就可能静默改变签名内容。
 *
 * <p>三个场景的键序差异（{@code scan} 把 {@code authCode} 放在 {@code notifyUrl} 之前、且
 * <b>不带 {@code payType}</b>）是旧实现的既有形态，本类原样保留。</p>
 */
public class PayCenterMessageFactory {

    private final PayCenterProperties properties;

    private final PayCenterSigner signer;

    public PayCenterMessageFactory(PayCenterProperties properties, PayCenterSigner signer) {
        this.properties = properties;
        this.signer = signer;
    }

    /** 预下单。目标 URL 取 {@link PayCenterProperties#getPayUrl()}，回调地址取通用 {@code payNoticeUrl}。 */
    public PayCenterRequest buildPayRequest(PayCenterPayCommand command) {
        return buildPayRequest(command, properties.getPayNoticeUrl());
    }

    /**
     * 预下单，<b>按笔指定支付结果回调地址</b>（网关文档 §支付下单：{@code notifyUrl} 可选、不传取商户默认配置）。
     *
     * <p>补款单（{@code SP} 前缀）走这个重载：通用 {@code payNoticeUrl} 指向 TVM 端点
     * {@code /itptvm/ci/tvm/payNotice}，那里只查 {@code F2F_ORDER}，补款回调必被拒
     * （{@code 2001 订单不存在}）并被支付中心反复重推，支付成功只能靠 5 分钟一轮的定时收敛兜住。见 ADR-D103。</p>
     *
     * <p><b>键序与单参重载完全一致、{@code notifyUrl} 仍是最后一个</b> —— 键序进待签串（见类注释），
     * 这里只换值不换位置，签名口径未变。<b>NEVER</b> 把这个 put 挪位置或提前。</p>
     */
    public PayCenterRequest buildPayRequest(PayCenterPayCommand command, String notifyUrl) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", command.orderNo());
        bizData.put("scene", command.scene().getCode());
        bizData.put("paymentVendor", command.effectivePaymentVendor());
        if (command.scene() != PayScene.SCAN && command.payType() != null) {
            bizData.put("payType", command.payType());
        }
        bizData.put("amount", command.amount());
        bizData.put("industryType", properties.getIndustryType());
        bizData.put("subject", command.subject());
        bizData.put("body", command.body());
        bizData.put("orderTimeOut", properties.getOrderTimeOutSeconds());
        if (command.scene() == PayScene.SCAN) {
            bizData.put("authCode", command.authCode());
        }
        bizData.put("notifyUrl", notifyUrl);
        return envelope(bizData);
    }

    /**
     * 支付结果查询。目标 URL 取 {@link PayCenterProperties#getQueryUrl()}。
     *
     * <p>入参是<b>我方订单号</b>，报文键名却是 {@code merchantOrderNo}，勿与响应里的
     * {@code orderNo}（支付中心订单号）混淆。</p>
     */
    public PayCenterRequest buildQueryRequest(String orderNo) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("merchantOrderNo", orderNo);
        return envelope(bizData);
    }

    /**
     * 退款。目标 URL 取 {@link PayCenterProperties#getRefundUrl()}。
     *
     * <p>三个订单号都要传：{@code refundOrderNo} 我方退款单号（幂等键）、
     * {@code merchantOrderNo} 我方原支付订单号、{@code orderNo} <b>支付中心侧</b>原订单号。
     * 后者为空时支付中心按前者定位，但旧实现始终传，此处保持一致。</p>
     */
    public PayCenterRequest buildRefundRequest(String refundNo, String origOrderNo,
                                               String payCenterOrderNo, long refundAmount) {
        if (refundAmount <= 0) {
            throw new IllegalArgumentException("refundAmount 必须为正数，单位分, refundNo=" + refundNo);
        }
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("refundOrderNo", refundNo);
        bizData.put("merchantOrderNo", origOrderNo);
        bizData.put("orderNo", payCenterOrderNo);
        bizData.put("refundAmount", refundAmount);
        bizData.put("refundReason", properties.getRefundReason());
        return envelope(bizData);
    }

    /** 退款结果查询。目标 URL 取 {@link PayCenterProperties#getRefundQueryUrl()}。 */
    public PayCenterRequest buildRefundQueryRequest(String refundNo) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("merchantRefundNo", refundNo);
        return envelope(bizData);
    }

    /**
     * {@code payType=0} 的本地分支：<b>不调支付中心</b>，把聚合码收银台 URL 拼出来直接返给设备，
     * 设备把整串显示成二维码、不解析内容。
     *
     * <p>照搬旧 {@code TvmOrderServiceImpl:96-103}。这条分支没有任何支付中心交互，因此
     * {@code F2F_PAYMENT} 该记什么状态在设计里仍是空的（见
     * {@code docs/architecture/face-pay-refactor.md} §十六 P1），实现落库前 MUST 先定口径。</p>
     */
    public String buildAggregateCodePayUrl(String orderNo) {
        String base = properties.getCheckoutCounterUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("pay.center.checkout-counter-url 未配置，payType=0 无法出码");
        }
        return base + "?orderNo=" + orderNo + "&sign=" + signer.aggregateCodeSign(orderNo);
    }

    /**
     * 组装信封并签名。
     *
     * <p><b>原始 JSON 要单独留一份传给签名器</b>：待签串取自 bizData 的<b>内部字段</b>
     * （对齐 pay-sign-server，见 {@link PayCenterSigner} 类注释），
     * 而信封里放的是它的 Base64。两者不能互相推导，因此不能只传 {@code request.getBizData()}。</p>
     */
    private PayCenterRequest envelope(Map<String, Object> bizData) {
        PayCenterRequest request = new PayCenterRequest();
        request.setMerchantNo(properties.getMerchantNo());
        request.setApiVersion(properties.getApiVersion());
        request.setSignType(properties.getSignType());
        request.setCharset(properties.getCharset());
        String bizDataJson = JSON.toJSONString(bizData);
        request.setBizData(Base64.getEncoder()
                .encodeToString(bizDataJson.getBytes(StandardCharsets.UTF_8)));
        signer.sign(request, bizDataJson);
        return request;
    }
}
