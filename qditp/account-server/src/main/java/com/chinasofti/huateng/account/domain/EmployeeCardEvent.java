package com.chinasofti.huateng.account.domain;

/**
 * {@code USER_ACC_EMPLOYEE_CARD_LOG.EVENT_TYPE} 的四个取值 —— <b>该列语义的唯一定义点</b>。
 *
 * <p>为什么要有这个枚举：这四个字符串此前以裸字面量散在 2 个类 6 处
 * （{@code EmployeeCardServiceImpl} 的 {@code OPEN} / {@code STATUS} / {@code CHANGE} 三处、
 * {@code EmployeeCardPersistenceServiceImpl} 的 {@code CANCEL} / {@code OPEN} /
 * {@code CANCEL}-{@code STATUS} 三元组），且 {@code recordEvent} 的形参是 {@code String}
 * —— 写错一个字母不报错、只是日志表里多出一个没人查得到的事件类型。
 * 换成枚举后<b>拼错即编译失败</b>。</p>
 *
 * <p>枚举名与落库字符串<b>刻意逐字一致</b>，落库取 {@link #name()}。
 * <b>NEVER 改名</b>：这列已有历史数据，改名等于把历史行与新行割成两类。</p>
 */
public enum EmployeeCardEvent {

    /** 开通：本地首次为该员工码建行。 */
    OPEN,
    /** 状态变更：激活 / 禁用，以及 ACC 下发的非注销状态通知。 */
    STATUS,
    /** 资料变更：换手机号等非状态字段的变化。 */
    CHANGE,
    /** 注销：{@code CARD_STATUS} 落到终态 4。 */
    CANCEL
}
