package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.notify.AppNotifyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GateTicketHandlerCardTranTest {

    private GateTicketHandler handler;
    private AppNotifyService appNotifyService;
    private GateDailyTicketCoordinator dailyTicketCoordinator;
    private GateCardTypeEnricher cardTypeEnricher;
    private GateTicketWriter gateTicketWriter;
    private QRCodeStatus effectiveStatus;

    @BeforeEach
    void setUp() {
        handler = new GateTicketHandler();
        appNotifyService = mock(AppNotifyService.class);
        dailyTicketCoordinator = mock(GateDailyTicketCoordinator.class);
        gateTicketWriter = mock(GateTicketWriter.class);
        effectiveStatus = new QRCodeStatus();
        effectiveStatus.setCodeStatus("05");

        QRCodeStatusStore store = mock(QRCodeStatusStore.class);
        QRCodeStatus currentStatus = new QRCodeStatus();
        currentStatus.setCodeStatus("04");
        currentStatus.setLastTxnStation("0101");
        when(store.findByCardId("C001")).thenReturn(currentStatus);

        cardTypeEnricher = mock(GateCardTypeEnricher.class);
        GateTxnAssembler assembler = mock(GateTxnAssembler.class);
        when(assembler.buildTxnDetail(any())).thenReturn(new QRCodeTxnDetail());
        when(assembler.buildNextStatus(any(), any())).thenReturn(effectiveStatus);
        when(dailyTicketCoordinator.checkEntryAllowed(any(), any(), anyString())).thenReturn(true);
        when(dailyTicketCoordinator.resolveCountingFlag("0441")).thenReturn("N");
        when(dailyTicketCoordinator.resolveCountingFlag("0442")).thenReturn("N");
        when(gateTicketWriter.saveTxnAndAdvanceStatus(any(), any(), any()))
                .thenReturn(new GateTicketWriter.WriteResult(false, 1, effectiveStatus));

        ReflectionTestUtils.setField(handler, "qrCodeStatusStore", store);
        ReflectionTestUtils.setField(handler, "gateTicketWriter", gateTicketWriter);
        ReflectionTestUtils.setField(handler, "cardTypeEnricher", cardTypeEnricher);
        ReflectionTestUtils.setField(handler, "txnAssembler", assembler);
        ReflectionTestUtils.setField(handler, "dailyTicketCoordinator", dailyTicketCoordinator);
        ReflectionTestUtils.setField(handler, "responseAssembler", mock(GateResponseAssembler.class));
        ReflectionTestUtils.setField(handler, "appNotifyService", appNotifyService);
        ReflectionTestUtils.setField(handler, "farePaymentOrchestrator", mock(GateFarePaymentOrchestrator.class));
    }

    @Test
    void 非HCE卡成功过闸推一次卡片行程通知() {
        NotifyVerifyResultReqDTO request = request("0441");

        handler.handleNotifyVerifyResult(request, "C001", new NotifyVerifyResultRespDTO());

        verify(appNotifyService).notifyCardTran(eq(request), eq(effectiveStatus), eq("N"));
    }

    @Test
    void HCE卡不推卡片行程通知且仍只回写卡数据() {
        NotifyVerifyResultReqDTO request = request("0442");

        handler.handleNotifyVerifyResult(request, "C001", new NotifyVerifyResultRespDTO());

        verify(appNotifyService, never()).notifyCardTran(any(), any(), any());
        verify(cardTypeEnricher).updateHceDataFromGateTransaction(request);
        verify(appNotifyService, never()).notifyVerifyResult(any(), any(), any());
    }

    @Test
    void 蓝牙渠道01不推卡片行程通知() {
        NotifyVerifyResultReqDTO request = request("0441");
        request.setChannelType("01");

        handler.handleNotifyVerifyResult(request, "C001", new NotifyVerifyResultRespDTO());

        verify(appNotifyService).notifyVerifyResult(eq(request), eq(effectiveStatus), any());
        verify(appNotifyService, never()).notifyCardTran(any(), any(), any());
    }

    @Test
    void 进站交易也推卡片行程通知() {
        NotifyVerifyResultReqDTO request = request("0441");
        request.setTrxType("01");

        handler.handleNotifyVerifyResult(request, "C001", new NotifyVerifyResultRespDTO());

        verify(appNotifyService).notifyCardTran(eq(request), eq(effectiveStatus), eq("N"));
    }

    @Test
    void 重复上送不重推卡片行程通知() {
        when(gateTicketWriter.saveTxnAndAdvanceStatus(any(), any(), any()))
                .thenReturn(new GateTicketWriter.WriteResult(true, 0, effectiveStatus));
        NotifyVerifyResultReqDTO request = request("0441");

        handler.handleNotifyVerifyResult(request, "C001", new NotifyVerifyResultRespDTO());

        verify(appNotifyService, never()).notifyCardTran(any(), any(), any());
    }

    private NotifyVerifyResultReqDTO request(String cardType) {
        NotifyVerifyResultReqDTO request = new NotifyVerifyResultReqDTO();
        request.setCardId("C001");
        request.setCardType(cardType);
        request.setTrxType("02");
        request.setTicketTransSeq("9");
        return request;
    }
}
