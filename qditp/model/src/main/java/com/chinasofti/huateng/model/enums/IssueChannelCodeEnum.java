package com.chinasofti.huateng.model.enums;

/**
 * 发行渠道编码枚举。
 * <p>对应票务系统 issueChannelCode 字段定义。</p>
 */
public enum IssueChannelCodeEnum {

    /** 01 - 正常渠道（闸机/APP 直接交易） */
    NORMAL("01", "正常渠道"),

    /** 07 - 支付宝渠道 */
    ALIPAY("07", "支付宝"),
    ;

    private final String code;
    private final String desc;

    /** 支付宝渠道的第三方用户号定长位数。 */
    private static final int ALIPAY_THIRD_USER_ID_LENGTH = 10;

    /** 其余渠道（含未知码）的第三方用户号定长位数。 */
    private static final int DEFAULT_THIRD_USER_ID_LENGTH = 8;

    IssueChannelCodeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据编码获取枚举，未知编码返回 null。
     */
    public static IssueChannelCodeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (IssueChannelCodeEnum e : values()) {
            if (e.code.equals(code.trim())) {
                return e;
            }
        }
        return null;
    }

    /**
     * 判断是否为支付宝渠道。
     */
    public static boolean isAlipay(String issueChannelCode) {
        return ALIPAY.code.equals(issueChannelCode);
    }

    /**
     * 该渠道要求的「第三方用户号」定长位数：支付宝 10 位，其余 8 位。
     *
     * <p>2026-09-14（ADR-D70）从两个模块上移到这里。此前长度散落在
     * {@code fep-dev-server} 的 {@code DeviceUserIdCodec.normalize} 与
     * {@code ticket-server} 的 {@code SupplementCodec.normalizeDeviceThirdUserId}
     * 两份实现里，而「谁算支付宝」的判断（{@link #isAlipay}）一直在本类 ——
     * <b>同一条渠道规则的两半分居两地</b>，这是上移的唯一理由。</p>
     *
     * <p><b>两处产出必须永远相等</b>：它们最终都写
     * {@code NotifyVerifyResultReqDTO.itpUserId}，一路流向
     * {@code QRCODE_TXN_DETAIL.ITP_USER_ID}，还都被行业卡通知与支付宝出行推送当
     * {@code thirdUserId} 发给下游。长度一旦分叉，同一个用户在同一张表里会出现两种位数，
     * 按它查历史必然漏行、且不报错。</p>
     *
     * <p><b>本方法只管长度，NEVER 把补位或进制转换搬进来</b>：fep-dev 侧收的是设备上送的
     * <b>十六进制</b>（要先 {@code new BigInteger(x, 16)} 转十进制），ticket-server 侧拿到的
     * 已经是十进制。把整个函数上移就得带一个 {@code boolean hexInput} 或两个重载，
     * 等于让 {@code model} 承担「AGM 用十六进制」这个只属于接入层的事实。</p>
     *
     * <p>未知 / null 渠道码返回 8，与上移前 {@code isAlipay ? 10 : 8} 的行为逐位一致 ——
     * <b>NEVER 改成先 {@link #fromCode} 再取实例属性</b>，那会给未知渠道引入一条 null 路径。</p>
     */
    public static int thirdUserIdLength(String issueChannelCode) {
        return isAlipay(issueChannelCode) ? ALIPAY_THIRD_USER_ID_LENGTH : DEFAULT_THIRD_USER_ID_LENGTH;
    }
}
