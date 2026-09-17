package com.chinasofti.huateng.ticket.ridestatus;

import com.chinasofti.huateng.model.app.MemberItineraryDTO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.station.StationNameResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** IF8A-29 行程装配的三态行为基线。 */
class MemberItineraryAssemblerTest {

    private static final String TRX_ENTRY = "01";
    private static final String TRX_EXIT = "02";
    private static final String TRX_EXIT_OVERTIME = "03";

    private StationNameResolver stationNameResolver;
    private MemberItineraryAssembler assembler;

    @BeforeEach
    void setUp() {
        stationNameResolver = mock(StationNameResolver.class);
        when(stationNameResolver.resolveNameOrCode(any(), any())).thenCallRealMethod();
        assembler = new MemberItineraryAssembler(stationNameResolver);
    }

    @Test
    void 无过闸记录时取状态表的末次交易字段() {
        QRCodeStatus status = status("0101", "20260914010203", "7");
        when(stationNameResolver.resolveStationNames(anySet())).thenReturn(Map.of("0101", "五四广场"));

        MemberItineraryDTO view = assembler.assemble(status, null);

        assertEquals("03", view.getTicketStatus());
        assertEquals("00", view.getPayStatus());
        assertEquals("0101", view.getThisStationCode());
        assertEquals("五四广场", view.getThisStationName());
        assertEquals("20260914010203", view.getThisTransTime());
        assertEquals("7", view.getTransSeq());
        assertNull(view.getLastStationCode());
    }

    @Test
    void 进站交易的上一站取末次处理站点() {
        QRCodeTxnDetail detail = detail(TRX_ENTRY);
        when(stationNameResolver.resolveStationNames(anySet()))
                .thenReturn(Map.of("0201", "李村", "0301", "青岛站"));

        MemberItineraryDTO view = assembler.assemble(status("FFFF", "0", "0"), detail);

        assertEquals("0201", view.getThisStationCode());
        assertEquals("李村", view.getThisStationName());
        assertEquals("0301", view.getLastStationCode());
        assertEquals("青岛站", view.getLastStationName());
        assertEquals("20260913220000", view.getLastTransTime());
    }

    @Test
    void 进站交易同时收集本站与末次站点编码() {
        assembler.assemble(status("FFFF", "0", "0"), detail(TRX_ENTRY));

        assertEquals(Set.of("0201", "0301"), capturedStationCodes());
    }

    @Test
    void 出站交易的上一站等于本站() {
        QRCodeTxnDetail detail = detail(TRX_EXIT);
        when(stationNameResolver.resolveStationNames(anySet())).thenReturn(Map.of("0201", "李村"));

        MemberItineraryDTO view = assembler.assemble(status("FFFF", "0", "0"), detail);

        assertEquals("0201", view.getThisStationCode());
        assertEquals("0201", view.getLastStationCode());
        assertEquals("李村", view.getLastStationName());
        assertEquals("20260914080000", view.getLastTransTime());
    }

    @Test
    void 出站交易只收集本站编码() {
        assembler.assemble(status("FFFF", "0", "0"), detail(TRX_EXIT));

        assertEquals(Set.of("0201"), capturedStationCodes());
    }

    @Test
    void 超时出站与正常出站同口径() {
        QRCodeTxnDetail detail = detail(TRX_EXIT_OVERTIME);
        when(stationNameResolver.resolveStationNames(anySet())).thenReturn(Map.of());

        MemberItineraryDTO view = assembler.assemble(status("FFFF", "0", "0"), detail);

        assertEquals("0201", view.getLastStationCode());
        assertEquals(Set.of("0201"), capturedStationCodes());
    }

    @Test
    void 站名查不到时降级回站点编码() {
        when(stationNameResolver.resolveStationNames(anySet())).thenReturn(Map.of());

        MemberItineraryDTO view = assembler.assemble(status("FFFF", "0", "0"), detail(TRX_ENTRY));

        assertEquals("0201", view.getThisStationName());
        assertEquals("0301", view.getLastStationName());
    }

    @Test
    void 实扣金额等于票价加超时费() {
        QRCodeTxnDetail detail = detail(TRX_EXIT);
        when(stationNameResolver.resolveStationNames(anySet())).thenReturn(Map.of());

        MemberItineraryDTO view = assembler.assemble(status("FFFF", "0", "0"), detail);

        assertEquals(200, view.getTransValue());
        assertEquals(50, view.getOvertimeTransValue());
        assertEquals(200, view.getOriTicketAmt());
        assertEquals(250, view.getDebitAmt());
        assertEquals(0, view.getOrderExpType());
        assertEquals("", view.getDiscountInfo());
        assertEquals(0, view.getCarbonDiscount());
    }

    @Test
    void 金额为空时实扣按零计算且原值保持空() {
        QRCodeTxnDetail detail = detail(TRX_EXIT);
        detail.setTrxAmount(null);
        detail.setOvertimeAmount(null);
        when(stationNameResolver.resolveStationNames(anySet())).thenReturn(Map.of());

        MemberItineraryDTO view = assembler.assemble(status("FFFF", "0", "0"), detail);

        assertNull(view.getTransValue());
        assertNull(view.getOvertimeTransValue());
        assertEquals(0, view.getDebitAmt());
    }

    private Set<String> capturedStationCodes() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
        verify(stationNameResolver).resolveStationNames(captor.capture());
        return captor.getValue();
    }

    private QRCodeStatus status(String lastTxnStation, String lastTxnTime, String txnSeq) {
        QRCodeStatus status = new QRCodeStatus();
        status.setCardId("0178229100072732");
        status.setCodeStatus("03");
        status.setLastTxnStation(lastTxnStation);
        status.setLastTxnTime(lastTxnTime);
        status.setTxnSeq(txnSeq);
        return status;
    }

    private QRCodeTxnDetail detail(String trxType) {
        QRCodeTxnDetail detail = new QRCodeTxnDetail();
        detail.setTrxType(trxType);
        detail.setHandleStationCode("0201");
        detail.setHandleDateTime("20260914080000");
        detail.setLastHandleStationCode("0301");
        detail.setLastHandleDateTime("20260913220000");
        detail.setTicketTransSeq("T20260914080000001");
        detail.setSignChannelCode("01");
        detail.setTrxAmount(200L);
        detail.setOvertimeAmount(50L);
        return detail;
    }
}
