package com.chinasofti.huateng.paysign.support;

import static com.chinasofti.huateng.paysign.support.PaySignValues.normalizeVendor;

import com.chinasofti.huateng.paysign.constant.PaymentVendorEnum;

/**
 * 「这笔请求走的是哪个支付渠道」的**唯一判定入口**（2026-09-16，ADR-D108）。
 *
 * <p><b>为什么必须有这个类</b>：收口前「是不是钱包」这个判断在 {@code src/main} 里被写了 <b>7 遍</b>、
 * {@code WALLET_PAYMENT_VENDOR} 常量有 <b>5 份副本</b>（4 个业务类 + {@code PayTxnRules}），
 * 而且**归一化时机三种写法并存**：
 * <ul>
 *   <li>{@code ContractDomainServiceImpl:328/:396} 拿已归一的局部变量比；</li>
 *   <li>{@code ContractDomainServiceImpl:507/:748}、{@code PaymentDomainServiceImpl:592}、
 *       {@code CallbackDomainServiceImpl:281}、{@code PayTxnRules:59} 现场调 {@code normalizeVendor}；</li>
 *   <li>{@code TerminationExecutor:210} 又是第三种。</li>
 * </ul>
 * 后果不是不好看：**新增一个渠道要改 7 处，漏一处既不编译失败也不告警**，只会让某一条链路把新渠道
 * 当成普通签约渠道处理（去支付中心查一个根本不存在的协议）。这与 AGENTS.md §2.2.1
 * 「多数模块用 String 字面量表达状态，改动状态值 MUST 全局 grep」是同一类风险的**活样例**。
 *
 * <p><b>准入判据同 {@link PaySignValues}</b>：零字段、零 IO、零协作者，给同样输入必得同样输出。
 * 因此本类只回答「是哪个渠道」，<b>NEVER 往里加「这个渠道该怎么处理」</b> —— 那属于业务分派，
 * 归领域服务；一旦加，本类就要注入 Bean，静态导入全部作废（同 {@code PaySignValues} 的告示）。
 *
 * <p><b>NEVER 在业务类里再写 {@code PaymentVendorEnum.WALLET.getCode()} 或字面量 {@code "0B"}</b>，
 * 也 NEVER 再声明本模块第 6 份 {@code WALLET_PAYMENT_VENDOR} 常量。
 */
public final class PaymentChannels {

    /**
     * 钱包渠道号。**对外只暴露 {@link #walletCode()}**，字段本身私有，避免又被别处 import 成第 6 份副本。
     */
    private static final String WALLET = PaymentVendorEnum.WALLET.getCode();

    private PaymentChannels() {
    }

    /**
     * 判断报文里的 paymentVendor 是否钱包渠道。
     *
     * <p>内部自行 {@code normalizeVendor}，因此**调用方传原始报文值即可**，不需要先归一。
     * 传已归一过的值也安全：{@code normalizeVendor} 只做 trim + 未知编码 warn，对已归一值幂等 ——
     * 唯一差别是未知编码会多打一行 warn，而那些调用点本来就已经打过一次。
     *
     * <p>{@code null} / 空串一律返回 {@code false}（{@code normalizeVendor} 对空值返回 {@code null}），
     * <b>NEVER 改成抛异常</b>：渠道字段在多个 APP 报文里是非必填的。
     */
    public static boolean isWallet(String paymentVendor) {
        return WALLET.equals(normalizeVendor(paymentVendor));
    }

    /**
     * 把报文里的 paymentVendor 归类成**处理类别**，给入口级路径选择用。
     *
     * <p>与 {@link #isWallet(String)} 的分工见 {@link PaymentChannel} 的类注释：
     * <b>选择处理链路 MUST 用本方法 + 穷尽 {@code switch}</b>，只问「是不是钱包」的谓词才用前者。
     * 两者共用同一个 {@code normalizeVendor}，因此永不会给出相反答案。
     *
     * <p>未知编码与 {@code null} 归到 {@link PaymentChannel.Contracted}，
     * 这是收口前的既有行为，NEVER 改成抛异常（见该 record 的注释）。
     */
    public static PaymentChannel classify(String paymentVendor) {
        String normalized = normalizeVendor(paymentVendor);
        return WALLET.equals(normalized)
                ? new PaymentChannel.Wallet(normalized)
                : new PaymentChannel.Contracted(normalized);
    }

    /**
     * 钱包渠道号本身。仅给「要把渠道号当值写出去」的地方用 —— 例如审计流水的 paymentVendor 段、
     * 调 {@code accountDomainPort.agreeRelease} 时的入参。判断请一律走 {@link #isWallet(String)}。
     */
    public static String walletCode() {
        return WALLET;
    }
}
