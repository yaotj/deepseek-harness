package com.chinasofti.huateng.account.entity;

import java.time.LocalDateTime;

/**
 * 账户域异常工单。
 */
public class AccountExceptionTicket {
    /**
     * 签约展示账号同步重试达上限。
     */
    public static final String TYPE_SIGN_SYNC_RETRY_EXHAUSTED = "SIGN_SYNC_RETRY_EXHAUSTED";

    /**
     * ACC 已受理员工码激活 / 禁用，但本地 {@code USER_ACC_EMPLOYEE_CARD} 状态回写失败。
     */
    public static final String TYPE_EMPLOYEE_CARD_STATUS_UNSYNCED = "EMPLOYEE_CARD_STATUS_UNSYNCED";

    /**
     * 开户已落库、但卡池确认（{@code confirm}）被拒或抛异常，卡号在池子里不是 {@code ASSIGNED}。
     */
    public static final String TYPE_CARD_POOL_CONFIRM_REJECTED = "CARD_POOL_CONFIRM_REJECTED";

    /**
     * 待人工处理。
     */
    public static final String STATUS_OPEN = "OPEN";

    /**
     * 已处理。
     */
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
