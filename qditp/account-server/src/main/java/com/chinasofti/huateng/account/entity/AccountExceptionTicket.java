package com.chinasofti.huateng.account.entity;

import java.time.LocalDateTime;

/**
 * 账户域异常工单。载体 {@code ACCOUNT_EXCEPTION_TICKET}。
 * <p>
 * 只接住**本域**无法自愈的情况（当前唯一来源：签约展示账号同步重试达上限）。
 * <b>其它域 NEVER 写这张表</b> —— 跨域共享一张工单表会让 owner 失焦，
 * 各域应建自己的同类表，判据见 {@code docs/domain/README.md} 的 owner 规则。
 * <p>
 * 幂等靠 {@code UK_ACCT_EXC_TICKET_TYPE_KEY (TICKET_TYPE, BIZ_KEY)}：
 * 同一笔业务在同一类型下**只会有一张工单**，补偿任务每 5 分钟重扫也不会刷出重复记录。
 */
public class AccountExceptionTicket {

    /** 签约展示账号同步重试达上限。{@code BIZ_KEY} 用 {@code USER_PHONE_CHANGE_LOG.ID}。 */
    public static final String TYPE_SIGN_SYNC_RETRY_EXHAUSTED = "SIGN_SYNC_RETRY_EXHAUSTED";

    /**
     * ACC 已受理员工码激活 / 禁用，但本地 {@code USER_ACC_EMPLOYEE_CARD} 状态回写失败。
     * {@code BIZ_KEY} 用 {@code 卡号:目标状态}（如 {@code 1234567890:1}）。
     */
    public static final String TYPE_EMPLOYEE_CARD_STATUS_UNSYNCED = "EMPLOYEE_CARD_STATUS_UNSYNCED";

    /**
     * 开户已落库、但卡池确认（{@code confirm}）被拒或抛异常，卡号在池子里不是 {@code ASSIGNED}。
     * {@code BIZ_KEY} 用 {@code reservationId}（一次预占只可能对应一个卡号，天然唯一）。
     *
     * <p>2026-09-14 新增（ADR-D52）。这类不一致**自愈不了**：账户表已经把卡号发给用户，
     * 而池子那边可能已被并发的兄弟请求 {@code release} 回 {@code AVAILABLE}，
     * 下一个开户请求就能把同一张卡号发给别人。处置动作在库外（人工核对后把池子那行对齐到
     * {@code ASSIGNED}），**NEVER 让任何自动流程去改卡池状态** —— 分不清「该对齐」还是
     * 「该真的释放」。</p>
     */
    public static final String TYPE_CARD_POOL_CONFIRM_REJECTED = "CARD_POOL_CONFIRM_REJECTED";

    /** 待人工处理。 */
    public static final String STATUS_OPEN = "OPEN";

    /** 已处理。关单只由人工触发，NEVER 由补偿任务自动关。 */
    public static final String STATUS_CLOSED = "CLOSED";

    private Long id;
    private String ticketType;
    private String bizKey;
    private String thirdUserId;
    private String ticketStatus;
    private Integer retryCount;
    private String detail;
    private LocalDateTime createTms;
    private LocalDateTime closeTms;
    private String closedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getTicketStatus() {
        return ticketStatus;
    }

    public void setTicketStatus(String ticketStatus) {
        this.ticketStatus = ticketStatus;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public LocalDateTime getCloseTms() {
        return closeTms;
    }

    public void setCloseTms(LocalDateTime closeTms) {
        this.closeTms = closeTms;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }
}
