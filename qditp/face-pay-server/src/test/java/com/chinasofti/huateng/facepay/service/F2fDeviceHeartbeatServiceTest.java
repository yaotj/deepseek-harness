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

/**
 * 锁死心跳落库的语义。
 *
 * <p>重点在「车站码按设备号补算」：该字段是运营端按车站定位设备的唯一来源，
 * 一旦退化回传 null，{@code STATION_CODE} 会重新全表为空，而页面不会报错、只会安静地少一列。
 * 解析规则本身的边界见 {@link com.chinasofti.huateng.facepay.support.F2fDeviceIdTest}。
 */
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

    /** 调用方显式给了车站就以显式值为准，推导只作兜底。 */
    @Test
    void explicitStationCodeWinsOverDerivedOne() {
        when(deviceStatusMapper.mergeHeartbeat(any(), any(), any(), any(LocalDateTime.class))).thenReturn(1);

        assertTrue(service.recordHeartbeat(F2fChannel.BOM, "06220701", "  0352  "));

        assertEquals("0352", capturedStationCode());
    }

    /** 设备号不足 4 位时站码留空，SQL 侧 NVL 会保留原值、不会把已有站码抹掉。 */
    @Test
    void leavesStationCodeNullWhenDeviceIdCannotDeriveIt() {
        when(deviceStatusMapper.mergeHeartbeat(any(), any(), any(), any(LocalDateTime.class))).thenReturn(1);

        assertTrue(service.recordHeartbeat(F2fChannel.TVM, "062", null));

        assertEquals(null, capturedStationCode());
    }

    /** 设备号缺失时整条心跳都不落库，也不该去推车站码。 */
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
