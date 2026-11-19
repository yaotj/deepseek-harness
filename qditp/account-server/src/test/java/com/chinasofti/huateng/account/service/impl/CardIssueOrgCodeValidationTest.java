package com.chinasofti.huateng.account.service.impl;

import com.chinasofti.huateng.model.enums.CardIssueOrgEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 发卡机构代码验证测试
 * 验证不做强限制，支持任意发卡机构代码
 */
class CardIssueOrgCodeValidationTest {

    @Test
    @DisplayName("已登记的发卡机构代码")
    void registeredIssueOrgCodes() {
        // 验证所有已登记的发卡机构
        assertEquals(CardIssueOrgEnum.SEA_BUS, CardIssueOrgEnum.fromCode("0004"));
        assertEquals(CardIssueOrgEnum.ALIPAY_TRIP, CardIssueOrgEnum.fromCode("0007"));
        assertEquals(CardIssueOrgEnum.CHENGDU_METRO, CardIssueOrgEnum.fromCode("0008"));
        assertEquals(CardIssueOrgEnum.QINGDAO_METRO_LEGACY, CardIssueOrgEnum.fromCode("0020"));
        assertEquals(CardIssueOrgEnum.QINGDAO_METRO, CardIssueOrgEnum.fromCode("5412"));
        assertEquals(CardIssueOrgEnum.CXUH_MINI_APP, CardIssueOrgEnum.fromCode("5413"));
        assertEquals(CardIssueOrgEnum.DIGITAL_CURRENCY, CardIssueOrgEnum.fromCode("0009"));
    }

    @Test
    @DisplayName("未登记的发卡机构代码返回 null")
    void unregisteredIssueOrgCodeReturnsNull() {
        assertNull(CardIssueOrgEnum.fromCode("9999"));
        assertNull(CardIssueOrgEnum.fromCode("1234"));
        assertNull(CardIssueOrgEnum.fromCode("ABCD"));
    }

    @Test
    @DisplayName("isRegistered 方法")
    void isRegisteredMethod() {
        assertTrue(CardIssueOrgEnum.isRegistered("0004"));
        assertTrue(CardIssueOrgEnum.isRegistered("0007"));
        assertTrue(CardIssueOrgEnum.isRegistered("0008"));
        assertTrue(CardIssueOrgEnum.isRegistered("0020"));
        assertTrue(CardIssueOrgEnum.isRegistered("5412"));
        assertTrue(CardIssueOrgEnum.isRegistered("5413"));
        assertTrue(CardIssueOrgEnum.isRegistered("0009"));

        assertFalse(CardIssueOrgEnum.isRegistered("9999"));
        assertFalse(CardIssueOrgEnum.isRegistered(null));
        assertFalse(CardIssueOrgEnum.isRegistered(""));
    }

    @Test
    @DisplayName("getDesc 方法")
    void getDescMethod() {
        assertEquals("海上巴士", CardIssueOrgEnum.getDesc("0004"));
        assertEquals("支付宝出行", CardIssueOrgEnum.getDesc("0007"));
        assertEquals("数币乘车码", CardIssueOrgEnum.getDesc("0009"));
        assertEquals("未知发卡机构", CardIssueOrgEnum.getDesc("9999"));
    }

    @Test
    @DisplayName("toIssueChannelCode4 已登记代码")
    void toIssueChannelCode4Registered() {
        // 已登记的发卡机构使用对应的发行渠道
        assertEquals("0001", CardIssueOrgEnum.toIssueChannelCode4("0004")); // 海上巴士 -> NORMAL(01)
        assertEquals("0007", CardIssueOrgEnum.toIssueChannelCode4("0007")); // 支付宝出行 -> ALIPAY(07)
        assertEquals("0001", CardIssueOrgEnum.toIssueChannelCode4("0008")); // 成都地铁 -> NORMAL(01)
        assertEquals("0001", CardIssueOrgEnum.toIssueChannelCode4("0020")); // 青岛地铁(早期) -> NORMAL(01)
        assertEquals("0001", CardIssueOrgEnum.toIssueChannelCode4("5412")); // 青岛地铁 -> NORMAL(01)
        assertEquals("0001", CardIssueOrgEnum.toIssueChannelCode4("5413")); // 畅行U惠小程序 -> NORMAL(01)
        assertEquals("0001", CardIssueOrgEnum.toIssueChannelCode4("0009")); // 数币乘车码 -> NORMAL(01)
    }

    @Test
    @DisplayName("toIssueChannelCode4 未登记代码使用默认渠道")
    void toIssueChannelCode4UnregisteredUsesDefault() {
        // 未登记的发卡机构使用默认渠道 NORMAL(01) -> "0001"
        assertEquals("0001", CardIssueOrgEnum.toIssueChannelCode4("9999"));
        assertEquals("0001", CardIssueOrgEnum.toIssueChannelCode4("1234"));
        assertEquals("0001", CardIssueOrgEnum.toIssueChannelCode4(null));
        assertEquals("0001", CardIssueOrgEnum.toIssueChannelCode4(""));
    }

    @Test
    @DisplayName("代码自动 trim")
    void codeAutoTrim() {
        assertEquals(CardIssueOrgEnum.SEA_BUS, CardIssueOrgEnum.fromCode(" 0004 "));
        assertTrue(CardIssueOrgEnum.isRegistered(" 0007 "));
    }

    @Test
    @DisplayName("所有枚举值唯一")
    void allCodesAreUnique() {
        CardIssueOrgEnum[] values = CardIssueOrgEnum.values();
        long distinctCodes = java.util.Arrays.stream(values)
                .map(CardIssueOrgEnum::getCode)
                .distinct()
                .count();
        assertEquals(values.length, distinctCodes, "发卡机构代码必须唯一");
    }
}
