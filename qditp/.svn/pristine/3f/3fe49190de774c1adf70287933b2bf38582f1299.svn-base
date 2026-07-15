package com.chinasofti.huateng.paysign.constant;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 签约渠道枚举
 * 定义所有支持的签约渠道编码和名称
 */
public enum SignChannelEnum {

    METRO_APP("METRO_APP", "地铁APP"),
    ALIPAY("ALIPAY", "支付宝"),
    WECHAT("WECHAT", "微信"),
    UNION_PAY("UNION_PAY", "云闪付"),
    LONG_PAY("LONG_PAY", "龙支付"),
    WALLET("WALLET", "钱包");

    private final String code;
    private final String name;

    SignChannelEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    private static final Map<String, SignChannelEnum> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(SignChannelEnum::getCode, e -> e));

    public static SignChannelEnum fromCode(String code) {
        return code == null ? null : CODE_MAP.get(code.trim());
    }

    public static boolean isValid(String code) {
        return code != null && CODE_MAP.containsKey(code.trim());
    }
}
