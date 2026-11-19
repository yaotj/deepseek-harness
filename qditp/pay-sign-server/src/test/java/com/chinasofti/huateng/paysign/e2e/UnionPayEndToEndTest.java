package com.chinasofti.huateng.paysign.e2e;

import com.chinasofti.huateng.paysign.constant.PaymentVendorEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 云闪付渠道端到端测试
 * 模拟从注册到支付的完整流程
 */
class UnionPayEndToEndTest {

    @Test
    @DisplayName("E2E: 云闪付渠道注册和支付流程")
    void unionPayEndToEndFlow() {
        // 步骤1: APP 发起注册请求，选择云闪付渠道
        String appSelectedChannel = "07"; // 云闪付渠道编码

        // 步骤2: 验证渠道编码有效
        assertTrue(PaymentVendorEnum.isValid(appSelectedChannel),
                "云闪付渠道编码应该有效");

        // 步骤3: 解析渠道
        PaymentVendorEnum vendor = PaymentVendorEnum.fromCode(appSelectedChannel);
        assertEquals(PaymentVendorEnum.UNION_PAY, vendor);
        assertEquals("云闪付", vendor.getName());

        // 步骤4: 模拟存储到数据库
        String storedChannel = vendor.getCode();
        assertEquals("07", storedChannel);

        // 步骤5: 支付时从数据库读取渠道
        PaymentVendorEnum loadedVendor = PaymentVendorEnum.fromCode(storedChannel);
        assertNotNull(loadedVendor, "应该能正确加载云闪付渠道");

        // 步骤6: 验证支付渠道分类
        assertFalse(PaymentVendorEnum.WALLET.equals(loadedVendor),
                "云闪付不应被分类为钱包");
    }

    @Test
    @DisplayName("E2E: 云闪付与其他渠道的区分")
    void unionPayVsOtherChannels() {
        // 验证云闪付与支付宝的区分
        PaymentVendorEnum unionPay = PaymentVendorEnum.fromCode("07");
        PaymentVendorEnum alipay = PaymentVendorEnum.fromCode("03");

        assertNotEquals(unionPay, alipay, "云闪付和支付宝应该是不同渠道");
        assertNotEquals(unionPay.getCode(), alipay.getCode());

        // 验证云闪付与微信的区分
        PaymentVendorEnum wechat = PaymentVendorEnum.fromCode("04");
        assertNotEquals(unionPay, wechat, "云闪付和微信应该是不同渠道");
    }

    @Test
    @DisplayName("E2E: 云闪付渠道边界情况")
    void unionPayEdgeCases() {
        // 测试带空格的编码
        assertTrue(PaymentVendorEnum.isValid(" 07 "));
        assertEquals(PaymentVendorEnum.UNION_PAY, PaymentVendorEnum.fromCode(" 07 "));

        // 测试 null
        assertFalse(PaymentVendorEnum.isValid(null));
        assertNull(PaymentVendorEnum.fromCode(null));

        // 测试空字符串
        assertFalse(PaymentVendorEnum.isValid(""));
        assertNull(PaymentVendorEnum.fromCode(""));

        // 测试无效编码
        assertFalse(PaymentVendorEnum.isValid("99"));
        assertNull(PaymentVendorEnum.fromCode("99"));
    }

    @Test
    @DisplayName("E2E: 所有支付渠道完整性")
    void allPaymentVendorsIntegrity() {
        PaymentVendorEnum[] allVendors = PaymentVendorEnum.values();

        // 验证包含主要渠道
        boolean hasUnionPay = false;
        boolean hasAlipay = false;
        boolean hasWechat = false;

        for (PaymentVendorEnum vendor : allVendors) {
            switch (vendor) {
                case UNION_PAY -> hasUnionPay = true;
                case ALIPAY -> hasAlipay = true;
                case WECHAT -> hasWechat = true;
            }
        }

        assertTrue(hasUnionPay, "应该包含云闪付渠道");
        assertTrue(hasAlipay, "应该包含支付宝渠道");
        assertTrue(hasWechat, "应该包含微信渠道");
    }
}
