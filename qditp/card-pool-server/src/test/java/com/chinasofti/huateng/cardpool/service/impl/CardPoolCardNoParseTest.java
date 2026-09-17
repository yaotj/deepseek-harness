package com.chinasofti.huateng.cardpool.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 锁定 ACC 逻辑卡号文件的行解析结果。 */
class CardPoolCardNoParseTest {

    @Test
    void 真实文件行取第一列作卡号() {
        assertEquals("0426090935000008",
                CardPoolServiceImpl.parseCardNo("0426090935000008 41", "41"));
    }

    @Test
    void 行尾CR被忽略() {
        assertEquals("0426090935000019",
                CardPoolServiceImpl.parseCardNo("0426090935000019 41\r", "41"));
    }

    @Test
    void 票种不一致判为非法() {
        assertNull(CardPoolServiceImpl.parseCardNo("0426090935000008 45", "41"));
    }

    @Test
    void 批次票种为空时不校验第二列() {
        assertEquals("0426090935000008",
                CardPoolServiceImpl.parseCardNo("0426090935000008 45", null));
    }

    @Test
    void 单列文件仍可解析() {
        assertEquals("0426090935000008",
                CardPoolServiceImpl.parseCardNo("0426090935000008", "41"));
    }

    @Test
    void 卡号含非法字符判为非法() {
        assertNull(CardPoolServiceImpl.parseCardNo("0426-0909-3500 41", "41"));
    }

    @Test
    void 空行与null判为非法() {
        assertNull(CardPoolServiceImpl.parseCardNo("   ", "41"));
        assertNull(CardPoolServiceImpl.parseCardNo(null, "41"));
    }
}
