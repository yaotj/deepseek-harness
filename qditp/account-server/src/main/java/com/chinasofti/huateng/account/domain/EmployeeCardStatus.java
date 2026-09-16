package com.chinasofti.huateng.account.domain;

/**
 * {@code USER_ACC_EMPLOYEE_CARD.CARD_STATUS} 的四个取值 —— <b>该列语义的唯一定义点</b>。
 *
 * <p>为什么要有这个枚举：这四个数字此前以裸字面量散在 3 个文件 6 处
 * （{@code EmployeeCardServiceImpl} 的激活 / 禁用白名单与合法性校验、
 * {@code EmployeeCardPersistenceServiceImpl} 的两个私有常量、`UserAccEmployeeCardMapper.xml`
 * 的 {@code CARD_STATUS = 1}），改动取值只能靠全局 grep。ACC 是这列的权威来源，
 * 一旦甲方调整编码，漏改任何一处都表现为「状态判断静默走错分支」，编译与单测都发现不了。</p>
 *
 * <p><b>数字取值由 ACC 定义，NEVER 自行调整</b>；也 <b>NEVER 依赖 {@code ordinal()}</b>
 * （声明顺序与编码无关），一律用 {@link #code()}。</p>
 *
 * <p>SQL 侧的字面量<b>无法</b>由本枚举收口（mapper XML 里的 {@code CARD_STATUS = 1} 仍是硬编码），
 * 改动取值时 MUST 连 {@code UserAccEmployeeCardMapper.xml} 一起改。</p>
 */
public enum EmployeeCardStatus {

    /** 1 正常（已激活可用）。{@code selectActiveByPhone} / {@code updateThirdUserId} 的「活跃」口径。 */
    NORMAL(1),
    /** 2 禁用（ACC 侧停用，可再激活）。 */
    DISABLED(2),
    /** 3 未激活（发卡后的起始态）。 */
    NOT_ENABLED(3),
    /** 4 注销（终态）。 */
    CANCELED(4);

    private final int code;

    EmployeeCardStatus(int code) {
        this.code = code;
    }

    /** 落库与报文使用的数字编码。 */
    public int code() {
        return code;
    }

    /**
     * 该状态是否等于给定编码。{@code null} 恒为 {@code false}
     * （IF3A 查询链路的 ACC 报文可能不带 {@code cardStatus}，见
     * {@code EmployeeCardPersistenceServiceImpl.refreshProfileFromAcc}）。
     */
    public boolean is(Integer status) {
        return status != null && status == code;
    }

    /**
     * 编码是否在四个已知取值内。
     *
     * <p>{@code EmployeeCardServiceImpl.validateCard} 用它替代原先的
     * {@code < 1 || > 4} 区间判断 —— 区间写法依赖「编码连续」这个偶然事实，
     * 甲方一旦新增非连续取值就会静默放行。</p>
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
