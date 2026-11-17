package com.chinasofti.huateng.dailyticket.service.lifecycle;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketUsageLogMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.service.DailyTicketAccActiveNotifyService;
import com.chinasofti.huateng.dailyticket.service.travel.TravelParentSummaryWriter;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class DailyTicketInstanceAccNoticeIsolationTest {

    private DailyTicketInstanceMapper instanceMapper;
    private DailyTicketUsageLogMapper usageLogMapper;
    private TravelParentSummaryWriter travelParentSummaryWriter;
    private DailyTicketInstanceLifecycleService service;

    @BeforeEach
    void setUp() {
        DailyTicketOrderMapper orderMapper = mock(DailyTicketOrderMapper.class);
        TravelTicketOrderMapper travelOrderMapper = mock(TravelTicketOrderMapper.class);
        instanceMapper = mock(DailyTicketInstanceMapper.class);
        usageLogMapper = mock(DailyTicketUsageLogMapper.class);
        DailyTicketAccActiveNotifyService accActiveNotifyService =
                mock(DailyTicketAccActiveNotifyService.class);
        travelParentSummaryWriter = mock(TravelParentSummaryWriter.class);
        service = new DailyTicketInstanceLifecycleService(orderMapper, travelOrderMapper,
                instanceMapper, usageLogMapper, accActiveNotifyService, travelParentSummaryWriter);
    }

    @Test
    void markUsed_keepsFailedAccNoticeStatus() {
        DailyTicketInstance instance = activatedInstance("FAIL");
        when(instanceMapper.selectByCardNum("0426090954000021")).thenReturn(instance);
        when(instanceMapper.decreaseActualTimes(anyString(), any())).thenReturn(1);
        when(instanceMapper.markUsed(any())).thenReturn(1);

        DailyTicketBaseResult result =
                service.markUsed("0426090954000021", 1790148164953L, "0E1", "0622", "0622");

        assertEquals("0000", result.getRetCode());
        DailyTicketInstance written = captureMarkUsed();
        assertEquals("EXPIRED", written.getTicketStatus());
        assertEquals("FAIL", written.getAccNoticeStatus());
        assertNull(written.getAccNoticeTime());
    }

    @Test
    void updateAndNotice_keepsPendingAccNoticeStatus() {
        DailyTicketInstance instance = activatedInstance("PENDING");
        when(instanceMapper.selectByCardNum("0426090954000021")).thenReturn(instance);
        when(instanceMapper.markUsed(any())).thenReturn(1);

        DailyTicketUsedNoticeReqDTO request = new DailyTicketUsedNoticeReqDTO();
        request.setCardNum("0426090954000021");
        request.setCountingEnd(1790148164953L);

        DailyTicketBaseResult result = service.updateAndNotice(request);

        assertEquals("0000", result.getRetCode());
        DailyTicketInstance written = captureMarkUsed();
        assertEquals("USED", written.getTicketStatus());
        assertEquals("PENDING", written.getAccNoticeStatus());
        assertNull(written.getAccNoticeTime());
    }

    private DailyTicketInstance activatedInstance(String accNoticeStatus) {
        DailyTicketInstance instance = new DailyTicketInstance();
        instance.setId("2fd1692600d8482e994499d72d288b46");
        instance.setOrderNo("0E202609221521550003");
        instance.setCardNum("0426090954000021");
        instance.setTicketStatus("ACTIVATED");
        instance.setActualTimes(1);
        instance.setAccNoticeStatus(accNoticeStatus);
        instance.setAccNoticeTimes(1);
        return instance;
    }

    private DailyTicketInstance captureMarkUsed() {
        ArgumentCaptor<DailyTicketInstance> captor =
                ArgumentCaptor.forClass(DailyTicketInstance.class);
        verify(instanceMapper).markUsed(captor.capture());
        return captor.getValue();
    }
}
