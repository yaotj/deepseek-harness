package com.chinasofti.huateng.ticket.ridestatus;

import com.chinasofti.huateng.model.app.MemberItineraryDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryReqDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryResult;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.gate.QRCodeStatusStore;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class TicketRideStatusServiceImplTest {

    private QRCodeStatusStore statusStore;
    private QRCodeTxnDetailMapper detailMapper;
    private MemberItineraryAssembler assembler;
    private TicketRideStatusServiceImpl service;

    @BeforeEach
    void setUp() {
        statusStore = mock(QRCodeStatusStore.class);
        detailMapper = mock(QRCodeTxnDetailMapper.class);
        assembler = mock(MemberItineraryAssembler.class);
        service = new TicketRideStatusServiceImpl(statusStore, detailMapper, assembler,
                "01", "03", "00", "FFFF", "FFFF");
        when(statusStore.findByCardId(any())).thenReturn(new QRCodeStatus());
        when(assembler.assemble(any(), any())).thenReturn(new MemberItineraryDTO());
    }

    private QueryUserItineraryReqDTO req() {
        QueryUserItineraryReqDTO request = new QueryUserItineraryReqDTO();
        request.setCardNum("0426090949000508");
        return request;
    }

    @Test
    void 无过闸明细时返8911() {
        when(detailMapper.selectLatestByCardId(any())).thenReturn(null);

        QueryUserItineraryResult result = service.queryUserItinerary(req());

        assertEquals("8911", result.getRetCode());
        assertEquals("上次行程为空", result.getRetMsg());
    }

    @Test
    void 刚进站有交易时返0000即使上次站点是哨兵() {
        QRCodeTxnDetail entry = new QRCodeTxnDetail();
        entry.setTrxType("01");
        entry.setHandleStationCode("1325");
        entry.setLastHandleStationCode("FFFF");
        when(detailMapper.selectLatestByCardId(any())).thenReturn(entry);

        QueryUserItineraryResult result = service.queryUserItinerary(req());

        assertEquals("0000", result.getRetCode());
    }

    @Test
    void 刚出站有交易时返0000() {
        QRCodeTxnDetail exit = new QRCodeTxnDetail();
        exit.setTrxType("02");
        exit.setHandleStationCode("0201");
        when(detailMapper.selectLatestByCardId(any())).thenReturn(exit);

        QueryUserItineraryResult result = service.queryUserItinerary(req());

        assertEquals("0000", result.getRetCode());
    }
}
