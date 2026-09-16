package com.chinasofti.huateng.facepay.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code F2F_ORDER.ORDER_STATUS} 的状态机。三件套（{@code docs/domain/state-machines.md} §二）的第 ① 件：
 * <b>枚举 + 迁移白名单</b>。
 *
 * <p><b>值域权威是 DDL 的 CHECK 约束</b>，不是本枚举：
 * {@code f2f-schema.sql:39} 的 {@code CK_F2F_ORDER_STATUS} 列了 11 个取值，本枚举逐字对齐。
 * 加状态 MUST 同时改那条约束（需 migration 脚本），<b>只改枚举会在写入时报 ORA-02290</b>。</p>
 *
 * <p><b>并发保证不在这里，在 CAS 的 WHERE</b>（三件套第 ② 件，已存在于
 * {@code F2fOrderMapper.xml} 的 {@code updateStatus} / {@code markPaid} / {@code activateForDevice}）。
 * 本枚举只负责<b>解析与文档化</b>，{@link #canTransitTo} 只做快速失败与错误提示，
 * <b>NEVER 当作并发保证</b>，也 <b>NEVER 在 CAS 前加它做前置校验</b> ——
 * 调用方手里的「当前状态」来自更早一次 select、随时可能过期，用过期值提前拦只会误拦，
 * 且比 CAS 返 0 行更难排查（{@code state-machines.md} §二③ 约束 2 与 ADR-D40 的裁决）。</p>
 *
 * <p><b>为什么放模块内而不是 {@code model/domain/}</b>：规范原文写的是放 {@code model/domain/}，
 * 这里有意偏离。{@code F2F_ORDER} 是 {@code face-pay-server} 独占的表，塞进 {@code model}
 * 会让 21 个模块共享一个只有一处使用的类，还要背上「给 {@code model} 加东西 MUST 重建链路上
 * 所有模块镜像」那条代价（AGENTS.md §7）。先例是规范自己点名的参考实现
 * {@code recon-server/.../recon/model/ReconBatchStatus.java} —— 它也在模块内。</p>
 *
 * <p><b>两个「有取值、无写入方」的状态</b>（2026-09-14 全模块 grep 实测）：
 * {@code TOPUP_SUSPECT} 只出现在 DDL 与 {@code F2fOrderMapper.xml} 的两处投影 CASE 里；
 * {@code CANCELED} 只出现在四个服务的 {@code FAILED_LIKE} 读取集合里。
 * <b>主代码没有任何地方把订单写成这两个状态</b>，因此白名单里它们没有入边 ——
 * 这是照实记录，不是遗漏。哪天补上写入方，MUST 同时在这里补入边。</p>
 */
public enum F2fOrderStatus {

    /** 已下单，未发起支付。 */
    CREATED,
    /** 已向支付中心发起支付，结果未定。 */
    PAYING,
    /** 支付成功。{@code markPaid} 的唯一目标状态，副作用列 {@code PAID_TMS}。 */
    PAID,
    /** 业务已履约（出票 / 充值成功）。副作用列 {@code FULFILL_TMS}。 */
    FULFILLED,
    /** 业务履约失败（出票失败 / 充值失败），后续通常进 {@code REFUNDING}。 */
    FULFILL_FAILED,
    /** 充值可疑，需人工核实。<b>当前无写入方</b>，见类注释。 */
    TOPUP_SUSPECT,
    /** 支付失败（支付中心明确拒绝或返回失败）。终态。 */
    PAY_FAILED,
    /** 二维码超时未支付 / 支付中心判定未支付。终态。 */
    EXPIRED,
    /** 退款已提交，等待支付中心收口。 */
    REFUNDING,
    /** 退款成功。终态。 */
    REFUNDED,
    /** 订单取消。<b>当前无写入方</b>，见类注释。终态。 */
    CANCELED;

    /**
     * 迁移白名单，逐条来自 2026-09-14 对 9 个服务全部 40 余个写入点的实测，**不是设计稿**。
     *
     * <p>五个终态（{@code PAY_FAILED} / {@code EXPIRED} / {@code REFUNDED} / {@code CANCELED}
     * / {@code TOPUP_SUSPECT}）没有出边。{@code TOPUP_SUSPECT} 连入边也没有，见类注释。</p>
     */
    private static final Map<F2fOrderStatus, Set<F2fOrderStatus>> ALLOWED = Map.of(
            CREATED, EnumSet.of(PAYING, PAID, PAY_FAILED, EXPIRED),
            PAYING, EnumSet.of(PAID, PAY_FAILED, EXPIRED),
            PAID, EnumSet.of(FULFILLED, FULFILL_FAILED, REFUNDING),
            FULFILLED, EnumSet.of(REFUNDING),
            FULFILL_FAILED, EnumSet.of(REFUNDING),
            REFUNDING, EnumSet.of(REFUNDED),
            PAY_FAILED, EnumSet.noneOf(F2fOrderStatus.class),
            EXPIRED, EnumSet.noneOf(F2fOrderStatus.class),
            REFUNDED, EnumSet.noneOf(F2fOrderStatus.class),
            CANCELED, EnumSet.noneOf(F2fOrderStatus.class));

    /**
     * 「未支付、可继续支付」。原先在 {@code F2fTvmOrderService} / {@code F2fTopupService}
     * / {@code F2fAppOrderService} / {@code F2fBomOrderService} 各有一份 {@code PENDING}，
     * 在 {@code F2fScanPayService} 还叫 {@code PAYABLE} —— 同一个集合五处定义、两个名字。
     */
    public static final List<String> PENDING = names(CREATED, PAYING);

    /**
     * 「已收到钱、可退」。原先 {@code F2fAppOrderService} 用枚举常量拼、
     * {@code F2fPageRefundService} 与 {@code F2fDeviceRefundService} 各写一份裸字面量。
     */
    public static final List<String> REFUNDABLE = names(PAID, FULFILLED, FULFILL_FAILED);

    /** 「失败态」读取集合，四个服务各有一份，其中三份带裸字面量 {@code "EXPIRED"} / {@code "CANCELED"}。 */
    public static final List<String> FAILED_LIKE = names(PAY_FAILED, EXPIRED, CANCELED);

    /** 「已支付待履约」，出票 / 充值成功失败四个写入点的前置状态。 */
    public static final List<String> FULFILLABLE = names(PAID);

    /**
     * 宽松解析：库里的脏值 / 新加但代码未识别的取值一律返 {@code null}，<b>NEVER 抛异常</b>。
     * 排查链路时拿到 null 只说明「本代码不认识这个状态」，不代表数据非法。
     */
    public static F2fOrderStatus parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        for (F2fOrderStatus st : values()) {
            if (st.name().equals(trimmed)) {
                return st;
            }
        }
        return null;
    }

    /** 只做快速失败与错误提示，NEVER 当作并发保证。并发保证是 CAS 的 WHERE。 */
    public boolean canTransitTo(F2fOrderStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** 终态即无出边。 */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    private static List<String> names(F2fOrderStatus... statuses) {
        return List.of(java.util.Arrays.stream(statuses).map(Enum::name).toArray(String[]::new));
    }
}
