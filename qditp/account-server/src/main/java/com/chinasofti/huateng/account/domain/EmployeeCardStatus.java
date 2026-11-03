package com.chinasofti.huateng.account.domain;

/**
 * {@code USER_ACC_EMPLOYEE_CARD.CARD_STATUS} 的四个取值 —— <b>该列语义的唯一定义点</b>。
 */
public enum EmployeeCardStatus {
    /**
     * 1 正常（已激活可用）。
     */
    NORMAL(1),
    /**
     * 2 禁用（ACC 侧停用，可再激活）。
     */
    DISABLED(2),
    /**
     * 3 未激活（发卡后的起始态）。
     */
    NOT_ENABLED(3),
    /**
     * 4 注销（终态）。
     */
    CANCELED(4);

    private final int code;

    EmployeeCardStatus(int code) {
        this.code = code;
    }

    /**
     * 落库与报文使用的数字编码。
     */
    public int code() {
        return code;
    }

    /**
     * 该状态是否等于给定编码。
     */
    public boolean is(Integer status) {
        return status != null && status == code;
    }

    /**
     * 是否允许作为「激活 / 启用」（APP {@code actionFlag=1}）的前置状态。
     *
     * <p>白名单只有两条边：{@code 3 未激活 → 1 启用}（首次激活）、
     * {@code 2 禁用 → 1 启用}（停用后重新启用，见 IT-044）。
     * {@code 1}（已启用）与 {@code 4}（注销终态）一律拒绝。**NEVER 改成「非终态即可」**。</p>
     */
    public static boolean activatableFrom(Integer status) {
        return NOT_ENABLED.is(status) || DISABLED.is(status);
    }

    /**
     * 编码是否在四个已知取值内。
     */
    public static boolean isKnownCode(Integer status) {
        if (status == null) {
            return false;
        }
        for (EmployeeCardStatus value : values()) {
            if (value.code == status) {
                return true;
            }
        }
        return false;
    }
}
