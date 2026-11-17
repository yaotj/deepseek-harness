package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.SignStatus;
import com.chinasofti.huateng.model.domain.SyncStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusWhitelistTest {

    @Test
    void signStatusWhitelistMatchesCasStatements() {
        assertTrue(SignStatus.NOT_SIGNED.canTransitTo(SignStatus.SIGNED));
        assertTrue(SignStatus.NOT_SIGNED.canTransitTo(SignStatus.FAILED));
        assertTrue(SignStatus.FAILED.canTransitTo(SignStatus.SIGNED));
        assertTrue(SignStatus.FAILED.canTransitTo(SignStatus.NOT_SIGNED));
        assertTrue(SignStatus.SIGNED.canTransitTo(SignStatus.UNSIGNED));
        assertTrue(SignStatus.UNSIGNED.canTransitTo(SignStatus.NOT_SIGNED));
    }

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

    @Test
    void onlyPendingAndFailedAreCompensable() {
        assertTrue(SyncStatus.PENDING.isCompensable());
        assertTrue(SyncStatus.FAILED.isCompensable());
        assertFalse(SyncStatus.SUCCESS.isCompensable());
    }

    @Test
    void syncSuccessIsTerminal() {
        assertTrue(SyncStatus.SUCCESS.isTerminal());
        assertFalse(SyncStatus.SUCCESS.canTransitTo(SyncStatus.PENDING));
        assertFalse(SyncStatus.SUCCESS.canTransitTo(SyncStatus.FAILED));
        assertTrue(SyncStatus.PENDING.canTransitTo(SyncStatus.FAILED));
        assertTrue(SyncStatus.FAILED.canTransitTo(SyncStatus.FAILED));
        assertTrue(SyncStatus.FAILED.canTransitTo(SyncStatus.SUCCESS));
    }

    @Test
    void syncNullIsNotPending() {
        assertNull(SyncStatus.parseOrNull(null));
        assertNull(SyncStatus.parseOrNull("   "));
    }
}
