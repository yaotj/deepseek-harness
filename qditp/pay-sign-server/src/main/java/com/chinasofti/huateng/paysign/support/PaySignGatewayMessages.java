package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发往**支付中心网关**的 6 组 {@code bizData} 组装（2026-09-15 拆分批次 3）。
 *
 * <p><b>这是出网报文的唯一组装处</b>：签约（contract）、授信查询（creditQuery）、
 * 签约结果查询（queryResult）、解约（dismissal）、免密扣款（requestPay）、退款（requestRefund）。
 * 改任何一个键名或取值来源前 <b>MUST 先核对 {@code docs/external/支付中心网关接口文档.md}</b>
 * —— 那是供方原文，不是我方设计。
 *
 * <p><b>本类刻意不持有 {@code PaySignProperties}</b>：{@code notifyUrl} 由调用方解析好再传进来
 * （签约走 {@code resolveNotifyUrl}、扣款走 {@code resolvePayNotifyUrl}，两者的兜底顺序不同）。
 * 于是本类是纯函数、可以逐字段断言组装结果，而「回调地址怎么兜底」仍留在业务类里。
 * <b>NEVER 往本类注入配置或 mapper</b>，那会立刻让它变回不可测。
 *
 * <p><b>用 {@link LinkedHashMap} 不是随手写的</b>：出网报文要参与加签，
 * 键序影响待签串，<b>NEVER 换成 {@code HashMap}</b>。
 *
 * <p><b>{@code requestRefund} 的 {@code orderNo} MUST 取 {@code PAY_CENTER_ORDER_NO}</b>：
 * 网关 §3.1 的 {@code orderNo} 指「支付中心侧那笔支付的订单号」。历史实现传的是
 * {@code refundDetail.getMerchantRefundNo()}，而该字段要等退款应答回来才由
 * {@code updateRefundRequestResult} 写入，构造报文时必然为 {@code null} —— 等于必填项漏传。
 * <b>NEVER 回退</b>。
 *
 * <p>本类与 {@code PaySignResponses} / {@code PaySignValidators} 同属对 AGENTS.md §5.1
 * 「NEVER 主动创建新的工具类」的有意破例，理由同 {@code F2fDuplicateKey}（ADR-D84）。
 */
public final class PaySignGatewayMessages {

    private PaySignGatewayMessages() {
    }

    /** IF8A-16 正式签约（contract）。{@code notifyUrl} 由调用方按「请求值 → 配置默认 → returnUrl」解析后传入。 */
    public static Map<String, Object> buildContractBizData(RequestSignInfoReqDTO request,
                                                           String paymentVendor,
                                                           String notifyUrl) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("requestSignSeq", request.getRequestSignSeq());
        bizData.put("paymentVendor", paymentVendor);
        bizData.put("thirdUserId", request.getThirdUserId());
        bizData.put("displayAccount", request.getDisplayAccount());
        putIfHasText(bizData, "notifyUrl", notifyUrl);
        putIfHasText(bizData, "returnUrl", request.getReturnUrl());
        putIfHasText(bizData, "options", request.getOptions());
        putIfHasText(bizData, "authCode", request.getAuthCode());
        // 以下为支付平台文档定义的通道扩展字段，按需透传。
        putIfHasText(bizData, "mobilePhone", request.getMobilePhone());
        putIfHasText(bizData, "certNo", request.getCertNo());
        putIfHasText(bizData, "custName", request.getCustName());
        putIfHasText(bizData, "token", request.getToken());
        putIfHasText(bizData, "payUserId", request.getPayUserId());
        putIfHasText(bizData, "bankCardNo", request.getBankCardNo());
        return bizData;
    }

    /** IF8A-21 授信查询（creditQuery）。 */
    public static Map<String, Object> buildCreditQueryBizData(String thirdUserId, String requestSignSeq, String paymentVendor) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("thirdUserId", thirdUserId);
        bizData.put("requestSignSeq", requestSignSeq);
        bizData.put("paymentVendor", paymentVendor);
        return bizData;
    }

    /**
     * IF8A-22 签约结果查询（queryResult）。
     *
     * <p>解约收口也用这个报文：网关 §2.4 的 {@code status} 同时承载签约态与解约态，
     * {@code status=UNSIGNED} 即已解约。支付中心**没有**独立的解约结果查询接口。
     */
    public static Map<String, Object> buildQueryResultBizData(String requestSignSeq) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("requestSignSeq", requestSignSeq);
        return bizData;
    }

    /**
     * IF8A-06 请求解约（dismissal）。
     *
     * <p>与 {@link #buildQueryResultBizData} 键集恰好相同，<b>但两者 NEVER 合并</b>：
     * 它们打的是两个不同的网关端点（{@code termination-url} / {@code contract-result-url}），
     * 供方任一侧加字段时只应改动其中一个。
     */
    public static Map<String, Object> buildDismissalBizData(String requestSignSeq) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("requestSignSeq", requestSignSeq);
        return bizData;
    }

    /**
     * IF8A-19 免密扣款（requestPay）。{@code notifyUrl} 由调用方按「请求值 → 支付专用配置」解析后传入。
     *
     * <p><b>NEVER 往本报文加 {@code payUserId}</b>（2026-09-16 删除，ADR-D92 续）。
     * 网关文档 §1.1 的字段表<b>没有这个字段</b>，它只属于 §2.2 contract（注为「数字人民币子钱包推送专用」）；
     * 接口方给出的钱包扣款「正确请求」样例里也没有它。而 {@code PayGatewayClient.buildSignSource}
     * 把 bizData 全部非空字段按 {@code TreeMap} 升序拼进待签串 —— <b>多送一个未定义字段就是另一个签名</b>，
     * 对端表现为不带 {@code msg} 的裸 {@code code=9999}（2026-09-16 实测 orderNo=LHTEST202609160003）。
     *
     * <p>钱包的 {@code payUserId} 仍然照旧落 {@code PAY_TXN_DETAIL.PAY_USER_ID}（对账与排查要用），
     * 只是**不出网**；因此 {@code applyAccountUserView} 那边的赋值 <b>NEVER 一起删</b>。
     */
    public static Map<String, Object> buildRequestPayBizData(RequestPayReqDTO request, String notifyUrl) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", request.getOrderNo());
        bizData.put("scene", request.getScene());
        bizData.put("paymentVendor", request.getPaymentVendor());
        bizData.put("amount", request.getAmount());
        bizData.put("industryType", request.getIndustryType());
        bizData.put("subject", request.getSubject());
        bizData.put("body", request.getBody());
        putIfHasText(bizData, "requestSignSeq", request.getRequestSignSeq());
        putIfHasText(bizData, "thirdUserId", request.getThirdUserId());
        putIfHasText(bizData, "industryDetail", request.getIndustryDetail());
        if (request.getOrderTimeOut() != null) {
            bizData.put("orderTimeOut", request.getOrderTimeOut());
        }
        putIfHasText(bizData, "authCode", request.getAuthCode());
        putIfHasText(bizData, "notifyUrl", notifyUrl);
        putIfHasText(bizData, "returnUrl", request.getReturnUrl());
        putIfHasText(bizData, "ipAddress", request.getIpAddress());
        putIfHasText(bizData, "remark", request.getRemark());
        return bizData;
    }

    /** 请求退款（requestRefund）。{@code orderNo} 取原支付的 {@code PAY_CENTER_ORDER_NO}，见类注释。 */
    public static Map<String, Object> buildRequestRefundBizData(PayRefundDetail refundDetail, PayTxnDetail payTxn) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("refundOrderNo", refundDetail.getRefundOrderNo());
        bizData.put("merchantOrderNo", payTxn.getOrderNo());
        bizData.put("orderNo", payTxn.getPayCenterOrderNo());
        bizData.put("refundAmount", refundDetail.getRefundAmount());
        bizData.put("refundReason", refundDetail.getRefundReason());
        return bizData;
    }

    /**
     * 空值字段一律**不进报文**，而不是进报文后取值为 null。
     *
     * <p>两者对加签串与供方解析都不等价，<b>NEVER 改成无条件 put</b>。
     */
    private static void putIfHasText(Map<String, Object> params, String key, String value) {
        if (StringUtils.hasText(value)) {
            params.put(key, value);
        }
    }
}
