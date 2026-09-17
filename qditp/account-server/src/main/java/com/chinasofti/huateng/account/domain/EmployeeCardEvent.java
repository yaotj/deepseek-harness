package com.chinasofti.huateng.account.domain;

/**
 * {@code USER_ACC_EMPLOYEE_CARD_LOG.EVENT_TYPE} 的四个取值 —— <b>该列语义的唯一定义点</b>。
 */
public enum EmployeeCardEvent {
    /**
     * 开通：本地首次为该员工码建行。
     */
    OPEN,
    /**
     * 状态变更：激活 / 禁用，以及 ACC 下发的非注销状态通知。
     */
    STATUS,
    /**
     * 资料变更：换手机号等非状态字段的变化。
     */
    CHANGE,
    /**
     * 注销：{@code CARD_STATUS} 落到终态 4。
     */
    CANCEL
}
