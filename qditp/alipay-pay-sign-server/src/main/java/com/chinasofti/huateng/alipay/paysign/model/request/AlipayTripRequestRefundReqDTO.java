package com.chinasofti.huateng.alipay.paysign.model.request;

import lombok.Data;

/**
 * 支付宝出行-退款申请请求参数（接收端）。
 *
 * <p><b>与 `model` 里的同名类 {@code com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundReqDTO}
 * 是同一条链路上的一对，字段 MUST 保持一致（当前都是 `orderNo` + `refundAmount` 两个）</b>：
 * 上游 {@code fep-alipay} 用 model 那份序列化、本模块用本类反序列化，
 * Fastjson2 宽松模式**静默丢弃**本类没有的字段 —— 两边字段数一旦不等，多出来的那些就**永久无效**且不报错。
 * 2026-09-20 修正过一次：model 那份曾多出 `cardIssueCode` / `cardNum` / `channelAgreementNo` / `refundOrderNo`
 * 四个，调用方按 6 字段传值全部落空；已把 model 那份裁回两字段。
 * <b>改本类字段 MUST 同批改 model 那份，NEVER 只改一边。</b>
 *
 * <p><b>那四个字段是刻意不接收的</b>：`cardNum` 取原支付单的 `CARD_ID`、`channelAgreementNo` 查
 * `ALIPAY_SIGN_INFO` 的生效签约、`cardIssueCode` 是渠道常量 `0007`、`refundOrderNo` 由服务端生成
 * （受 `UK_ARL_REFUND_ORDER_NO` 唯一索引约束），全部由 {@code AlipayPayRefundServiceImpl} 自己解析。
 * <b>NEVER 加回来改成「信调用方送的值」</b> —— 那等于把退款单号与扣款账户的决定权交给调用方。
 */
@Data
public class AlipayTripRequestRefundReqDTO {
    /**
     * 原订单号
     */
    private String orderNo;

    /**
     * 退款金额，单位分；不传则默认全额退款
     */
    private String refundAmount;
}
