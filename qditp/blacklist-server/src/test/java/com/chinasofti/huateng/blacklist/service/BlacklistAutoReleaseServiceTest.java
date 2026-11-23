package com.chinasofti.huateng.blacklist.service;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单卡实时解黑的候选判据是本次新增的核心逻辑。
 *
 * <p>NOT using Mockito here: blacklist-server 模块在 JDK 25 下 mock ProxyWebClient 子类
 * （GateTxnPayClient / CardUnsettledQuery）会抛 MockitoException，属既有环境限制
 * （BlacklistQueryFailedCountTest 同样受影响）。编排/判据 mock 交由 face-pay 侧单测与测试环境 E2E 覆盖，
 * 本类只锁纯静态判据，不依赖任何 mock。
 */
class BlacklistAutoReleaseServiceTest {

    private static Blacklist record(String status, String cause) {
        Blacklist b = new Blacklist();
        b.setCardId("0426090949000508");
        b.setStatus(status);
        b.setBlackCause(cause);
        return b;
    }

    @Test
    void activeUnpaidRecordIsEligible() {
        assertTrue(BlacklistAutoReleaseService.isRealtimeEligible(record("ACTIVE", "01")),
                "ACTIVE + 欠费类(01) 是唯一可实时解黑的候选，与全表 selectForAutoRelease 同口径");
    }

    @Test
    void releasingRecordIsNotEligible() {
        assertFalse(BlacklistAutoReleaseService.isRealtimeEligible(record("RELEASING", "01")),
                "解除中的行仍算黑名单，实时解黑 NEVER 碰它（渠道同步未完成）");
    }

    @Test
    void nonUnpaidCauseIsNotEligible() {
        assertFalse(BlacklistAutoReleaseService.isRealtimeEligible(record("ACTIVE", "02")),
                "非欠费原因码 NEVER 被欠费结清触发的实时解黑释放");
        assertFalse(BlacklistAutoReleaseService.isRealtimeEligible(record("ACTIVE", "09")));
    }

    @Test
    void nullRecordIsNotEligible() {
        assertFalse(BlacklistAutoReleaseService.isRealtimeEligible(null));
    }
}
