package com.chinasofti.huateng.paysign.support;

/**
 * 支付渠道的**处理类别**（2026-09-16，ADR-D109）。
 *
 * <p><b>它不是 {@code PaymentVendorEnum} 的替代品</b>：那个枚举有 12 个编码，
 * 回答「这是哪家渠道」；本类型只有两个变体，回答<b>「这笔请求该走哪条处理链路」</b>。
 * 两者是多对一：`03` 支付宝 / `04` 微信 / `05` 支付宝出行 / 四个 `CBDC_*` 等
 * 全部落到 {@link Contracted}，只有 `0B` 落到 {@link Wallet}。
 *
 * <p><b>为什么要 sealed，而不是继续用 {@code isWallet(...)} 的布尔判断</b>：布尔判断的 else
 * 隐含「其余一切都按传统签约处理」，因此<b>新增一个需要特殊处理的渠道时，漏改的那个分派点
 * 不会编译失败、不会告警，只会静默把新渠道当传统渠道处理</b>（去支付中心查一个不存在的协议）。
 * 而这不是假想：`PaymentVendorEnum` 里已有 `0C 数币APP` 与四个 `CBDC_*`，
 * AGENTS.md §2.2.2 记着数字人民币硬钱包共 7 个接口「docs 有规格、代码无实现」——
 * 第三个类别是**已经在路上的**既定事实。改成 sealed 后，那天只需在这里加一个变体，
 * <b>编译器会把每一个需要改的分派点逐个列出来</b>。同一条路子在本模块已有三个先例：
 * `RpcOutcome`（ADR-D45）、`AccountQuery`（ADR-D94）、`ContractResultOutcome`（ADR-D107）。
 *
 * <p><b>只在「入口级路径选择」处用穷尽 {@code switch}</b>（当前 6 处：三个签约/解约入口 +
 * 解约结果回调 + {@code validatePaySignInfo} + {@code TerminationExecutor} 的短路）。
 * 字段装配内部那种「是不是钱包」的谓词（{@code applyAccountUserView}、
 * {@code ContractDomainServiceImpl} 的通道有效性判断）<b>仍用 {@link PaymentChannels#isWallet}</b>
 * —— 那些地方不选择处理链路，套 {@code switch} 只增噪音。这条边界 NEVER 模糊掉：
 * 一旦到处都是 {@code switch}，加一个变体要改的点又变成「改不完也看不出漏没漏」。
 *
 * <p><b>NEVER 往变体里加行为方法</b>（如 {@code handle(...)}）。那会把「怎么处理」搬进
 * support 包，而处理需要注入 Bean，本类型立刻要变成 Spring Bean，静态导入全部作废 ——
 * 与 {@link PaySignValues} / {@link PaymentChannels} 的准入判据同源。
 */
public sealed interface PaymentChannel {

    /**
     * 钱包（{@code 0B}）：**没有支付中心代扣协议以外的签约咨询链路**，解绑由账户域
     * {@code requestAgreeRelease} 同步完成，不进 {@code APP_TERMINATION_REQUEST} 的 T+4 扫描。
     */
    record Wallet(String code) implements PaymentChannel {
    }

    /**
     * 走支付中心代扣签约的渠道（支付宝 / 微信 / 数币 …）。
     *
     * <p><b>未知编码与 {@code null} 也落这里</b>，这是**刻意保留的既有行为**：收口前
     * {@code isWallet(...)} 对未知编码返回 {@code false}、于是走传统链路，改成抛异常或另立变体
     * 都会改变对外行为（{@code normalizeVendor} 对未知编码只 warn 不拦）。
     * {@code code()} 因此可能是 {@code null}。</p>
     */
    record Contracted(String code) implements PaymentChannel {
    }

    /** 归一化后的渠道编码；{@link Contracted} 上可能为 {@code null}。 */
    String code();
}
