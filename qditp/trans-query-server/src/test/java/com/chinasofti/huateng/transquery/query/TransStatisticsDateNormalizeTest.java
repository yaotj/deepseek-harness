package com.chinasofti.huateng.transquery.query;

import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.TripDataDTO;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class TransStatisticsDateNormalizeTest {

    private GateTxnPayClient gateTxnPayClient;
    private TransStatisticsQueryHandler handler;

    @BeforeEach
    void setUp() {
        gateTxnPayClient = mock(GateTxnPayClient.class);
        handler = new TransStatisticsQueryHandler();
        ReflectionTestUtils.setField(handler, "gateTxnPayClient", gateTxnPayClient);
        ReflectionTestUtils.setField(handler, "paramNormalizer", new TransQueryParamNormalizer());
    }

    @Test
    void contractDateIsNormalizedToEightDigitsBeforeRpc() {
        when(gateTxnPayClient.requestTransStatistics(any())).thenReturn(statistics());

        RequestTransStatisticsResult result = handler.requestTransStatistics(request("2026-09-01", "2026-09-22"));

        assertEquals("0000", result.getRetCode());
        ArgumentCaptor<RequestTransStatisticsReqDTO> captor =
                ArgumentCaptor.forClass(RequestTransStatisticsReqDTO.class);
        verify(gateTxnPayClient).requestTransStatistics(captor.capture());
        assertEquals("20260901", captor.getValue().getStartDate());
        assertEquals("20260922", captor.getValue().getEndDate());
    }

    @Test
    void compactDateIsAcceptedAndPassedThrough() {
        when(gateTxnPayClient.requestTransStatistics(any())).thenReturn(statistics());

        RequestTransStatisticsResult result = handler.requestTransStatistics(request("20260901", "20260922"));

        assertEquals("0000", result.getRetCode());
        ArgumentCaptor<RequestTransStatisticsReqDTO> captor =
                ArgumentCaptor.forClass(RequestTransStatisticsReqDTO.class);
        verify(gateTxnPayClient).requestTransStatistics(captor.capture());
        assertEquals("20260901", captor.getValue().getStartDate());
        assertEquals("20260922", captor.getValue().getEndDate());
    }

    @Test
    void malformedDateIsRejectedAtEntry() {
        RequestTransStatisticsResult result = handler.requestTransStatistics(request("2026/09/01", "2026/09/22"));

        assertEquals("8001", result.getRetCode());
        verify(gateTxnPayClient, never()).requestTransStatistics(any());
    }

    @Test
    void reversedRangeIsRejected() {
        RequestTransStatisticsResult result = handler.requestTransStatistics(request("2026-09-22", "2026-09-01"));

        assertEquals("8001", result.getRetCode());
        verify(gateTxnPayClient, never()).requestTransStatistics(any());
    }

    @Test
    void absentDatesStayNull() {
        when(gateTxnPayClient.requestTransStatistics(any())).thenReturn(statistics());

        handler.requestTransStatistics(request(null, null));

        ArgumentCaptor<RequestTransStatisticsReqDTO> captor =
                ArgumentCaptor.forClass(RequestTransStatisticsReqDTO.class);
        verify(gateTxnPayClient).requestTransStatistics(captor.capture());
        assertEquals(null, captor.getValue().getStartDate());
        assertEquals(null, captor.getValue().getEndDate());
    }

    private RequestTransStatisticsReqDTO request(String startDate, String endDate) {
        RequestTransStatisticsReqDTO request = new RequestTransStatisticsReqDTO();
        request.setThirdUserId("00522950");
        request.setCardType("12");
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        return request;
    }

    private RequestTransStatisticsResult statistics() {
        RequestTransStatisticsResult result = new RequestTransStatisticsResult();
        TripDataDTO tripData = new TripDataDTO();
        tripData.setCount(3);
        tripData.setTotalPrice("11");
        result.setTripData(tripData);
        return result;
    }
}
