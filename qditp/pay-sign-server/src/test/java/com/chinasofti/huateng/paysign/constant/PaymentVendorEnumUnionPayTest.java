package com.chinasofti.huateng.paysign.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PaymentVendorEnum 云闪付渠道测试
 * 验证 UNION_PAY("07", "云闪付") 的定义和识别
 */
class PaymentVendorEnumUnionPayTest {

    @Test
    @DisplayName("UNION_PAY 枚举值正确")
    void unionPayEnumValue() {
        PaymentVendorEnum unionPay = PaymentVendorEnum.UNION_PAY;
        assertEquals("07", unionPay.getCode());
        assertEquals("云闪付", unionPay.getName());
    }

    @Test
    @DisplayName("fromCode(\"07\") 返回 UNION_PAY")
    void fromCodeReturnsUnionPay() {
        PaymentVendorEnum result = PaymentVendorEnum.fromCode("07");
        assertEquals(PaymentVendorEnum.UNION_PAY, result);
    }

    @Test
    @DisplayName("isValid(\"07\") 返回 true")
    void isValidReturnsTrue() {
        assertTrue(PaymentVendorEnum.isValid("07"));
    }

    @Test
    @DisplayName("fromCode(\" 07 \") 自动 trim 并返回 UNION_PAY")
    void fromCodeTrimsWhitespace() {
        PaymentVendorEnum result = PaymentVendorEnum.fromCode(" 07 ");
        assertEquals(PaymentVendorEnum.UNION_PAY, result);
    }

    @Test
    @DisplayName("所有定义的支付渠道编码唯一")
    void allCodesAreUnique() {
        PaymentVendorEnum[] values = PaymentVendorEnum.values();
        long distinctCodes = java.util.Arrays.stream(values)
                .map(PaymentVendorEnum::getCode)
                .distinct()
                .count();
        assertEquals(values.length, distinctCodes, "支付渠道编码必须唯一");
    }

    @Test
    @DisplayName("UNION_PAY 在 allCodes() 输出中")
    void unionPayInAllCodes() {
        String allCodes = PaymentVendorEnum.allCodes();
        assertTrue(allCodes.contains("07"));
    }
}
