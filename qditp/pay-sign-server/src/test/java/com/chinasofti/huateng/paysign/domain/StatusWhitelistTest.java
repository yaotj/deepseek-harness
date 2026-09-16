package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.SignStatus;
import com.chinasofti.huateng.model.domain.SyncStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 迁移白名单的回归测试。断言与 {@code PaySignInfoMapper.xml} 的 4 条 CAS、
 * {@code UserPhoneChangeLogMapper.xml} 的扫表条件**逐条对齐**；
 * 改任何一边 **MUST** 同时改另一边并让本类通过，否则枚举与 SQL 会出现两套口径。
 */
class StatusWhitelistTest {

    /** 2026-09-11 已在库上用合成数据实跑过同一组断言（见 decisions.md ADR-D12），此处是它的代码化留存。 */
    @Test
    void signStatusWhitelistMatchesCasStatements() {
        assertTrue(SignStatus.NOT_SIGNED.canTransitTo(SignStatus.SIGNED));
        assertTrue(SignStatus.NOT_SIGNED.canTransitTo(SignStatus.FAILED));
        assertTrue(SignStatus.FAILED.canTransitTo(SignStatus.SIGNED));
        assertTrue(SignStatus.FAILED.canTransitTo(SignStatus.NOT_SIGNED));
        assertTrue(SignStatus.SIGNED.canTransitTo(SignStatus.UNSIGNED));
        assertTrue(SignStatus.UNSIGNED.canTransitTo(SignStatus.NOT_SIGNED));
    }

    /**
     * 本条是整个改造要挡住的那一个迁移：迟到的签约回调把已解约通道改回已签约，
     * APP 会显示通道有效而渠道侧协议已注销。**NEVER 放开**。
     */
    @Test
    void unsignedCanNeverGoBackToSigned() {
        assertFalse(SignStatus.UNSIGNED.canTransitTo(SignStatus.SIGNED));
    }

    @Test
    void signStatusRejectsSelfLoopAndUnknownInput() {
        assertFalse(SignStatus.SIGNED.canTransitTo(SignStatus.SIGNED));
        assertFalse(SignStatus.SIGNED.canTransitTo(null));
        assertNull(SignStatus.parseOrNull(null));
        assertNull(SignStatus.parseOrNull(""));
        assertNull(SignStatus.parseOrNull("signed"));
        assertNull(SignStatus.parseOrNull("SIGNING"));
        assertSame(SignStatus.SIGNED, SignStatus.parseOrNull("SIGNED"));
    }

    /** 与扫表 SQL 的 {@code SIGN_SYNC_STATUS IN ('PENDING','FAILED')} 对齐。 */
    @Test
    void onlyPendingAndFailedAreCompensable() {
        assertTrue(SyncStatus.PENDING.isCompensable());
        assertTrue(SyncStatus.FAILED.isCompensable());
        assertFalse(SyncStatus.SUCCESS.isCompensable());
    }

    /** {@code SUCCESS} 是唯一终态；回退会导致对已送达的记录重复推送。 */
    @Test
    void syncSuccessIsTerminal() {
        assertTrue(SyncStatus.SUCCESS.isTerminal());
        assertFalse(SyncStatus.SUCCESS.canTransitTo(SyncStatus.PENDING));
        assertFalse(SyncStatus.SUCCESS.canTransitTo(SyncStatus.FAILED));
        assertTrue(SyncStatus.PENDING.canTransitTo(SyncStatus.FAILED));
        assertTrue(SyncStatus.FAILED.canTransitTo(SyncStatus.FAILED));
        assertTrue(SyncStatus.FAILED.canTransitTo(SyncStatus.SUCCESS));
    }

    /** 历史行该列为 NULL，语义是「不参与补偿」，NEVER 被解析成 PENDING。 */
    @Test
    void syncNullIsNotPending() {
        assertNull(SyncStatus.parseOrNull(null));
        assertNull(SyncStatus.parseOrNull("   "));
    }
}
