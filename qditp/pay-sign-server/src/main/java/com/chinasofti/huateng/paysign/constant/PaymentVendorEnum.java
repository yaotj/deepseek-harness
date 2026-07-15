package com.chinasofti.huateng.paysign.constant;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付渠道枚举
 * 定义所有支持的支付渠道编码和名称
 */
public enum PaymentVendorEnum {

    ALIPAY("03", "支付宝"),
    WECHAT("04", "微信"),
    ALIPAY_TRAVEL("05", "支付宝出行"),
    LONG_PAY("06", "龙支付"),
    CMB_BANK("0601", "招商银行"),
    BOC_BANK("0602", "中国银行"),
    CBDC_CONSTRUCTION("08", "建行数币"),
    CBDC_BOC("0801", "中行数币"),
    CBDC_PSBC("0802", "邮储数币"),
    CBDC_COMM("0803", "交行数币"),
    WALLET("0B", "钱包"),
    CBDC_APP("0C", "数币APP");

    private final String code;
    private final String name;

    PaymentVendorEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    // 静态缓存，提高查询性能
    private static final Map<String, PaymentVendorEnum> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(PaymentVendorEnum::getCode, e -> e));

    /**
     * 根据编码获取枚举
     */
    public static PaymentVendorEnum fromCode(String code) {
        return code == null ? null : CODE_MAP.get(code.trim());
    }

    /**
     * 判断编码是否有效
     */
    public static boolean isValid(String code) {
        return code != null && CODE_MAP.containsKey(code.trim());
    }

    /**
     * 获取所有编码字符串，用于日志或配置
     */
    public static String allCodes() {
        return CODE_MAP.keySet().toString();
    }
}
