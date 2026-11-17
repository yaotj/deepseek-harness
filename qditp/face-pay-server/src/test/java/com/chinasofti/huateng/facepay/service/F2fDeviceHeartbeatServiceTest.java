package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.mapper.F2fDeviceStatusMapper;
import com.chinasofti.huateng.facepay.support.F2fChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class F2fDeviceHeartbeatServiceTest {

    @Mock
    private F2fDeviceStatusMapper deviceStatusMapper;

    @InjectMocks
    private F2fDeviceHeartbeatService service;

    @Test
    void fallsBackToDerivedStationCodeWhenCallerPassesNone() {
        when(deviceStatusMapper.mergeHeartbeat(any(), any(), any(), any(LocalDateTime.class))).thenReturn(1);

        assertTrue(service.recordHeartbeat(F2fChannel.TVM, "06220701", null));

        assertEquals("0622", capturedStationCode());
    }

    @Test
    void explicitStationCodeWinsOverDerivedOne() {
        when(deviceStatusMapper.mergeHeartbeat(any(), any(), any(), any(LocalDateTime.class))).thenReturn(1);

        assertTrue(service.recordHeartbeat(F2fChannel.BOM, "06220701", "  0352  "));

        assertEquals("0352", capturedStationCode());
    }

    @Test
    void leavesStationCodeNullWhenDeviceIdCannotDeriveIt() {
        when(deviceStatusMapper.mergeHeartbeat(any(), any(), any(), any(LocalDateTime.class))).thenReturn(1);

        assertTrue(service.recordHeartbeat(F2fChannel.TVM, "062", null));

        assertEquals(null, capturedStationCode());
    }

    @Test
    void skipsPersistenceWhenDeviceIdMissing() {
        assertFalse(service.recordHeartbeat(F2fChannel.TVM, null, null));
        assertFalse(service.recordHeartbeat(F2fChannel.TVM, "   ", null));

        verifyNoInteractions(deviceStatusMapper);
    }

    @Test
    void reportsFalseWhenMergeAffectsNoRow() {
        when(deviceStatusMapper.mergeHeartbeat(any(), any(), any(), any(LocalDateTime.class))).thenReturn(0);

        assertFalse(service.recordHeartbeat(F2fChannel.TVM, "06220701", null));
    }

    @Test
    void passesChannelAndDeviceIdThroughUnchanged() {
        when(deviceStatusMapper.mergeHeartbeat(any(), any(), any(), any(LocalDateTime.class))).thenReturn(1);

        service.recordHeartbeat(F2fChannel.BOM, "02441101", null);

        verify(deviceStatusMapper).mergeHeartbeat(eq(F2fChannel.BOM), eq("02441101"), eq("0244"),
                any(LocalDateTime.class));
    }

    private String capturedStationCode() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(deviceStatusMapper).mergeHeartbeat(any(), any(), captor.capture(), any(LocalDateTime.class));
        return captor.getValue();
    }
}
