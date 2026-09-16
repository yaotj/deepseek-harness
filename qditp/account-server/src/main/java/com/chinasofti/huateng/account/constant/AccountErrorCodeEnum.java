package com.chinasofti.huateng.account.constant;

/**
 * 账户域返回码。
 *
 * <p><b>本枚举里同时存在两套「失败」语义，这是历史现状、不是笔误</b>：开户 / 支付通道 / 销户链路的
 * 系统类失败用 {@link #SYSTEM_ERROR}（{@code 9001}），而<b>员工码链路用 {@link #FAIL}</b>
 * （{@code 9999}）。ADR-D36 收口字面量时<b>刻意没有对齐取值</b>——这些码已经发给 APP 与 ACC，
 * 改值属改对外契约，MUST 先确认下游没在判这些码。<b>NEVER 为了「看起来整齐」把 9999 改成 9001。</b></p>
 */
public enum AccountErrorCodeEnum {
    SUCCESS("0000", "成功"),
    /** 批量处理里「部分成功、部分失败」。当前只有员工码批量状态通知用。 */
    PARTIAL_SUCCESS("0001", "部分处理失败"),
    FAIL("9999", "失败"),
    /**
     * ACC 已受理、但本地状态回写失败。<b>与 {@link #FAIL} 语义不同、NEVER 合并</b>：
     * 它意味着远端已生效而本地落后，调用方不该重试（重试会再打 ACC 一次），
     * 处置方式是等异常工单人工收口。
     */
    LOCAL_WRITE_BACK_FAILED("9998", "ACC已受理但本地状态回写失败"),
    SYSTEM_ERROR("9001", "系统内部错误"),
    INVALID_PARAM("8001", "无效的参数"),
    ALREADY_REGISTERED("8002", "已发卡"),
    NO_CARD_RESOURCE("8003", "暂无卡数据资源"),
    NO_ACCOUNT_CARD("8004", "没有账号卡片数据"),
    IN_BLACKLIST("8005", "您已进入黑名单请联系客服"),
    CARD_USER_MISMATCH("8006", "账号和卡号不匹配"),
    SERVICE_PROVIDER_UNAVAILABLE("8007", "服务提供商不可用"),
    TERMINATION_AUDITING("8008", "解约审核中"),
    NO_AVAILABLE_CA("8009", "ITP无可用CA证书"),
    DUPLICATE_SIGN("8010", "请勿重复签约"),
    USER_NOT_SIGNED("8011", "用户未签约"),
    TERMINATION_SIGNED("8012", "解除签约"),
    CANNOT_TERMINATE_DEFAULT("8013", "不允许解约默认支付渠道"),
    INVALID_SIGN_DATA("8014", "无效的签约数据"),
    ADD_PAY_CHANNEL_DUPLICATE("8021", "请勿重复添加支付渠道"),
    PAY_CHANNEL_NOT_FOUND("8022", "支付通道不存在"),
    UNSETTLED_ORDER_EXISTS("8023", "存在未支付或扣费失败的订单，请处理后再销户"),
    ACC_INFO_QUERY_FAIL("8024", "账务信息查询失败，请稍后重试"),
    ACC_COMM_ERROR("8501", "ACC通讯异常"),
    ACC_RETURN_STATUS_ABNORMAL("8502", "ACC返回值状态异常"),
    /** 员工码前置状态不满足（激活要求「未启用」、禁用要求「正常」）。取值 2002 由 ACC 侧规格给定。 */
    EMPLOYEE_CARD_STATUS_NOT_ALLOWED("2002", "电子卡当前状态不允许该操作");

    private final String code;
    private final String msg;

    AccountErrorCodeEnum(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
