package com.chinasofti.huateng.model.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 解约申请状态机。载体 {@code APP_TERMINATION_REQUEST.TERMINATION_STATUS}。
 * <p>
 * 流转（白名单，与 {@code AppTerminationRequestMapper.xml} 的 6 条 CAS 一一对应）：
 * <pre>
 *   PENDING  -&gt; SCANNING（markScanning 抢占执行权）| FAILED（rejectPending 有未结清欠费）
 *   SCANNING -&gt; SUCCESS（markSuccess 回调收口）| FAILED（rejectScanning 支付平台答复失败 /
 *                                               expireScanning 滞留超时）| PENDING（revertScanningToPending 交还执行权）
 *   FAILED   -&gt; PENDING（reactivateFailed 用户重新申请）
 *   SUCCESS  -&gt; 终态
 * </pre>
 * <b>NEVER 允许 {@code FAILED -> SUCCESS}</b>（用户 2026-09-12 裁决）。日级调度下
 * {@code SCANNING_TIMEOUT_MINUTES=1440} 会把滞留的申请打成 {@code FAILED} 并已给 APP 发过
 * 「解约失败」通知；此时若迟到的解约成功回调能直接改成 {@code SUCCESS}，APP 会先收到失败、
 * 再收到成功两条相反通知，而「已告知失败之后怎么补通知」是业务口径问题、不该由代码默认决定。
 * 该场景 MUST 走人工：{@code expired > 0} 的 {@code log.error} 已是告警出口，运维到支付中心
 * 核对协议真实状态后，由用户从 APP 重新申请（走 {@code FAILED -> PENDING} 复活）。
 * <p>
 * <b>NEVER 允许 {@code SUCCESS} 迁出</b>：解约成功后签约记录已 DELETE、账户域支付通道已清理，
 * 回退状态只会让补偿队列重新捞取、对 APP 重复投递。
 * <p>
 * 本枚举<b>只做解析与文档化，NEVER 当作并发保证</b>。并发保证唯一来自 mapper 的 CAS UPDATE，
 * 前置状态写在 SQL 的 WHERE 里；判定 CAS 结果用
 * {@code paysign.domain.TerminationStatusTransition}。规范见 {@code docs/domain/state-machines.md} §二。
 * <p>
 * <b>NEVER 改动这些常量的字面量</b>：库内存量数据按这些字符串存储，且 pay-sign-server 侧
 * 仍有历史 {@code private static final String} 常量在比较同一批值，改名等于制造两套口径。
 * 新增取值 MUST 同步 {@code ALLOWED} 与 mapper XML 的 CAS。
 */
public enum TerminationStatus {

    /** 待处理。APP 申请解约后落库的初始态，等扫表任务查欠费并调支付平台。 */
    PENDING,

    /** 扫描中。已调支付平台请求解约、等回调或主动查询收口，是<b>长期在途</b>状态。 */
    SCANNING,

    /** 解约成功。终态，签约记录已删、账户域通道已清理。 */
    SUCCESS,

    /** 解约失败。可由用户重新申请复活成 {@code PENDING}，NEVER 直接转 {@code SUCCESS}。 */
    FAILED;

    private static final Map<TerminationStatus, Set<TerminationStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(SCANNING, FAILED),
            SCANNING, EnumSet.of(SUCCESS, FAILED, PENDING),
            FAILED, EnumSet.of(PENDING),
            SUCCESS, EnumSet.noneOf(TerminationStatus.class));

    /**
     * 宽松解析：库内可能存在 NULL 或历史脏值，解析不出来时返回 {@code null} 而不抛异常，
     * 由调用方决定是拒绝还是当未知态跳过。<b>NEVER 在这里兜底成某个具体状态</b> ——
     * 猜错方向会把脏数据推进状态机。
     */
    public static TerminationStatus parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (TerminationStatus s : values()) {
            if (s.name().equals(raw)) {
                return s;
            }
        }
        return null;
    }

    /** 只做快速失败与错误提示，NEVER 当作并发保证。并发保证是 mapper 的 CAS。 */
    public boolean canTransitTo(TerminationStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * 无任何合法后继即终态。当前只有 {@code SUCCESS}；{@code FAILED} 不是终态
     * （可复活成 {@code PENDING}），但它<b>是通知补偿扫表的白名单成员</b>，两个概念勿混。
     */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    /**
     * 是否已有可发出的解约结果通知。与 {@code selectCompensableNotify} 的
     * {@code TERMINATION_STATUS in ('SUCCESS','FAILED')} 必须保持一致。
     * <p>
     * <b>NEVER 把 {@code PENDING} / {@code SCANNING} 纳入</b>：那两个态还没有结果可通知，
     * 滞留后会被补偿当成「成功通知丢了」，给 APP 发一条 {@code status=SUCCESS} 且
     * {@code dismissalTime} 为空的假解约成功通知（2026-08-26 已修复过一次）。
     */
    public boolean isNotifiable() {
        return this == SUCCESS || this == FAILED;
    }
}
