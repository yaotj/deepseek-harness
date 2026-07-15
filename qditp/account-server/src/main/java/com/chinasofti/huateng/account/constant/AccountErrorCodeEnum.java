package com.chinasofti.huateng.account.constant;

public enum AccountErrorCodeEnum {
    SUCCESS("0000", "成功"),
    FAIL("9999", "失败"),
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
    ACC_COMM_ERROR("8501", "ACC通讯异常"),
    ACC_RETURN_STATUS_ABNORMAL("8502", "ACC返回值状态异常");

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
