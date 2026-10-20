package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.api.page.F2fDeviceStatusPageVO;
import com.chinasofti.huateng.facepay.api.page.F2fDeviceStatusSummaryVO;
import com.chinasofti.huateng.facepay.mapper.F2fDeviceStatusMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 锁死 {@link F2fDeviceQueryService} 的入参校验、分页归一与 LIKE 转义语义。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class F2fDeviceQueryServiceTest {

    @Mock
    private F2fDeviceStatusMapper deviceStatusMapper;

    @InjectMocks
    private F2fDeviceQueryService service;

    private static F2fDeviceQueryService.DevicePageQuery query(String channel, String onlineFlag,
                                                               String deviceId, Integer pageNum, Integer pageSize) {
        return new F2fDeviceQueryService.DevicePageQuery(channel, onlineFlag, deviceId, pageNum, pageSize);
    }

    private static F2fDeviceStatusSummaryVO summary(long total, long online, long offline) {
        F2fDeviceStatusSummaryVO vo = new F2fDeviceStatusSummaryVO();
        vo.setTotal(total);
        vo.setOnlineCount(online);
        vo.setOfflineCount(offline);
        return vo;
    }

    @Test
    void rejectsUnknownChannelWithoutTouchingDatabase() {
        F2fDeviceQueryService.PageOutcome outcome = service.page(query("09", null, null, 1, 10));

        F2fDeviceQueryService.PageOutcome.Rejected rejected =
                assertInstanceOf(F2fDeviceQueryService.PageOutcome.Rejected.class, outcome);
        assertTrue(rejected.reason().contains("受理渠道"), rejected.reason());
        verifyNoInteractions(deviceStatusMapper);
    }

    @Test
    void rejectsUnknownOnlineFlagWithoutTouchingDatabase() {
        F2fDeviceQueryService.PageOutcome outcome = service.page(query(null, "2", null, 1, 10));

        F2fDeviceQueryService.PageOutcome.Rejected rejected =
                assertInstanceOf(F2fDeviceQueryService.PageOutcome.Rejected.class, outcome);
        assertTrue(rejected.reason().contains("在线状态"), rejected.reason());
        verifyNoInteractions(deviceStatusMapper);
    }

    @Test
    void acceptsAllThreeSupportedChannels() {
        for (String channel : List.of("01", "02", "03")) {
            when(deviceStatusMapper.countDevicePage(eq(channel), isNull(), isNull())).thenReturn(0L);
            when(deviceStatusMapper.selectDeviceSummary()).thenReturn(summary(0, 0, 0));
            when(deviceStatusMapper.selectDevicePage(eq(channel), isNull(), isNull(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            assertInstanceOf(F2fDeviceQueryService.PageOutcome.Ok.class,
                    service.page(query(channel, null, null, 1, 10)));
        }
    }

    @Test
    void clampsPageArgumentsAndComputesOffset() {
        when(deviceStatusMapper.selectDevicePage(isNull(), isNull(), isNull(), eq(900), eq(100)))
                .thenReturn(List.of(new F2fDeviceStatusPageVO()));
        when(deviceStatusMapper.countDevicePage(isNull(), isNull(), isNull())).thenReturn(901L);
        when(deviceStatusMapper.selectDeviceSummary()).thenReturn(summary(21, 16, 5));

        F2fDeviceQueryService.PageOutcome.Ok ok = assertInstanceOf(F2fDeviceQueryService.PageOutcome.Ok.class,
                service.page(query(null, null, null, 10, 1000)));

        assertEquals(901L, ok.total());
        assertEquals(1, ok.list().size());
        assertEquals(21L, ok.summary().getTotal());
    }

    @Test
    void fallsBackToDefaultsWhenPageArgumentsMissingOrNegative() {
        when(deviceStatusMapper.selectDevicePage(isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(List.of());
        when(deviceStatusMapper.countDevicePage(isNull(), isNull(), isNull())).thenReturn(0L);
        when(deviceStatusMapper.selectDeviceSummary()).thenReturn(summary(0, 0, 0));

        assertInstanceOf(F2fDeviceQueryService.PageOutcome.Ok.class,
                service.page(query(null, null, null, 0, -5)));
    }

    /** 用户输入的 LIKE 元字符 MUST 被转义，否则 % 会退化成通配符、悄悄放大命中集。 */
    @Test
    void escapesLikeMetacharactersBeforeReachingMapper() {
        when(deviceStatusMapper.selectDevicePage(isNull(), isNull(), eq("06\\%2\\_07\\\\01"), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(deviceStatusMapper.countDevicePage(isNull(), isNull(), eq("06\\%2\\_07\\\\01"))).thenReturn(0L);
        when(deviceStatusMapper.selectDeviceSummary()).thenReturn(summary(0, 0, 0));

        assertInstanceOf(F2fDeviceQueryService.PageOutcome.Ok.class,
                service.page(query(null, null, "06%2_07\\01", 1, 10)));

        verify(deviceStatusMapper).countDevicePage(isNull(), isNull(), eq("06\\%2\\_07\\\\01"));
    }

    @Test
    void blankDeviceKeywordBecomesNullSoTheFilterIsOmitted() {
        when(deviceStatusMapper.selectDevicePage(isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(deviceStatusMapper.countDevicePage(isNull(), isNull(), isNull())).thenReturn(0L);
        when(deviceStatusMapper.selectDeviceSummary()).thenReturn(summary(0, 0, 0));

        assertInstanceOf(F2fDeviceQueryService.PageOutcome.Ok.class,
                service.page(query(null, null, "   ", 1, 10)));
    }

    @Test
    void escapeLikeHandlesNullAndKeepsBackslashFirst() {
        assertNull(F2fDeviceQueryService.escapeLike(null));
        assertEquals("0622", F2fDeviceQueryService.escapeLike("0622"));
        assertEquals("\\%", F2fDeviceQueryService.escapeLike("%"));
        assertEquals("\\_", F2fDeviceQueryService.escapeLike("_"));
        assertEquals("\\\\", F2fDeviceQueryService.escapeLike("\\"));
        assertEquals("\\\\\\%", F2fDeviceQueryService.escapeLike("\\%"));
    }

    /** summary 是全表口径，MUST 原样透传、不被筛选条件改写。 */
    @Test
    void summaryIsPassedThroughUntouched() {
        F2fDeviceStatusSummaryVO summary = summary(21, 16, 5);
        summary.setLastHeartbeatTms("2026-10-15 17:00:00");
        when(deviceStatusMapper.selectDevicePage(eq("03"), eq("0"), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(new F2fDeviceStatusPageVO()));
        when(deviceStatusMapper.countDevicePage(eq("03"), eq("0"), isNull())).thenReturn(4L);
        when(deviceStatusMapper.selectDeviceSummary()).thenReturn(summary);

        F2fDeviceQueryService.PageOutcome.Ok ok = assertInstanceOf(F2fDeviceQueryService.PageOutcome.Ok.class,
                service.page(query("03", "0", null, 1, 10)));

        assertEquals("2026-10-15 17:00:00", ok.summary().getLastHeartbeatTms());
        assertEquals(16L, ok.summary().getOnlineCount());
        assertEquals(4L, ok.total());
    }
}
