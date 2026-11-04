package com.chinasofti.huateng.key.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AGM 密钥版本审批状态机白名单测试（AGENTS.md §4.2 范式）。
 *
 * <p>审批是安全红线动作，白名单必须逐条锁定：只放行 20010 初始化的两个出边，终态零出边，
 * 任何「非终态即可处理」的写法都会被本测试拦下。</p>
 */
class AgmKeyApprovalStatusWhitelistTest {

    @Test
    void initCanApproveAndReject() {
        assertTrue(AgmKeyApprovalStatus.INIT.canTransitTo(AgmKeyApprovalStatus.APPROVED), "20010 -> 20020 必须放行");
        assertTrue(AgmKeyApprovalStatus.INIT.canTransitTo(AgmKeyApprovalStatus.REJECTED), "20010 -> 20030 必须放行");
        assertFalse(AgmKeyApprovalStatus.INIT.canTransitTo(AgmKeyApprovalStatus.INIT), "INIT 不可自环");
        assertFalse(AgmKeyApprovalStatus.INIT.isTerminal(), "INIT 有出边，非终态");
    }

    @Test
    void approvedAndRejectedAreTerminal() {
        for (AgmKeyApprovalStatus st : AgmKeyApprovalStatus.values()) {
            if (st == AgmKeyApprovalStatus.APPROVED || st == AgmKeyApprovalStatus.REJECTED) {
                assertTrue(st.isTerminal(), st + " 必须终态");
                for (AgmKeyApprovalStatus target : AgmKeyApprovalStatus.values()) {
                    assertFalse(st.canTransitTo(target), st + " 不可再迁到 " + target + "（终态零出边）");
                }
            }
        }
    }

    @Test
    void noUnlistedTransitionAllowed() {
        // 白名单之外的迁移（如 APPROVED->REJECTED、REJECTED->APPROVED、INIT->INIT）一律拒绝。
        assertFalse(AgmKeyApprovalStatus.APPROVED.canTransitTo(AgmKeyApprovalStatus.REJECTED));
        assertFalse(AgmKeyApprovalStatus.REJECTED.canTransitTo(AgmKeyApprovalStatus.APPROVED));
        assertFalse(AgmKeyApprovalStatus.APPROVED.canTransitTo(AgmKeyApprovalStatus.INIT));
        assertFalse(AgmKeyApprovalStatus.REJECTED.canTransitTo(AgmKeyApprovalStatus.INIT));
    }

    @Test
    void parseByCodeIsLenient() {
        assertEquals("20010", AgmKeyApprovalStatus.INIT.getCode());
        assertEquals("20020", AgmKeyApprovalStatus.APPROVED.getCode());
        assertEquals("20030", AgmKeyApprovalStatus.REJECTED.getCode());
        assertNotNull(AgmKeyApprovalStatus.parseByCode("20020"));
        assertNotNull(AgmKeyApprovalStatus.parseByCode(" 20030 "));
        assertNull(AgmKeyApprovalStatus.parseByCode("99999"), "未识别取值必须返 null，NEVER 抛异常");
        assertNull(AgmKeyApprovalStatus.parseByCode(null));
        assertNull(AgmKeyApprovalStatus.parseByCode(""));
    }

    private static void assertEquals(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
