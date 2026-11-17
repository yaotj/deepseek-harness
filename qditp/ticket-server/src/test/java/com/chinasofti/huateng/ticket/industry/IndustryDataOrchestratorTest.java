package com.chinasofti.huateng.ticket.industry;

import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.RequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.app.RequestIndustryDataResult;
import com.chinasofti.huateng.model.app.RequestNoSignalDataReqDTO;
import com.chinasofti.huateng.model.app.RequestNoSignalDataResult;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.ticket.gate.AgmRideStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IndustryDataOrchestratorTest {

    private static final String THIRD_USER_ID = "0700009918";
    private static final String CARD_ID = "0426090949000384";
    private static final String CARD_TYPE_QR = "0441";
    private static final String CARD_TYPE_HCE = "0442";
    private static final String CARD_TYPE_ONE_DAY = "0445";

    private static final String HCE_CARD_DATA =
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF";

    @Mock
    private AccountClient accountClient;
    @Mock
    private DailyTicketClient dailyTicketClient;
    @Mock
    private AgmRideStatusService agmRideStatusService;
    @Mock
    private IndustryCardDataAssembler cardDataAssembler;

    private IndustryDataOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new IndustryDataOrchestrator(
                accountClient, dailyTicketClient, agmRideStatusService, cardDataAssembler);
    }

    @Test
    void hce卡短路生码直接返缓存卡数据() {
        givenUserInfo(CARD_TYPE_HCE, "07", HCE_CARD_DATA);

        RequestIndustryDataResult response = orchestrator.requestIndustryData(onlineRequest());

        assertEquals("0000", response.getRetCode());
        assertEquals(HCE_CARD_DATA, response.getCardData());
        verify(cardDataAssembler, never()).buildAndSign(any(), any(), anyString(), any(), any(), any());
        verify(agmRideStatusService, never()).queryQrCodeStatus(any());
    }

    @Test
    void hce卡缺卡数据时报错() {
        givenUserInfo(CARD_TYPE_HCE, "07", null);

        RequestIndustryDataResult response = orchestrator.requestIndustryData(onlineRequest());

        assertEquals("HCE卡数据不存在", response.getRetMsg());
        assertNull(response.getCardData());
    }


    @Test
    void hce卡数据格式非法时报错() {
        givenUserInfo(CARD_TYPE_HCE, "07", "HCEDATA0001");

        RequestIndustryDataResult response = orchestrator.requestIndustryData(onlineRequest());

        assertEquals("HCE卡数据不存在", response.getRetMsg());
        assertNull(response.getCardData());
    }

    @Test
    void 签约渠道为空返8001() {
        givenUserInfo(CARD_TYPE_QR, null, null);

        RequestIndustryDataResult response = orchestrator.requestIndustryData(onlineRequest());

        assertEquals("8001", response.getRetCode());
        assertEquals("用户签约渠道不能为空", response.getRetMsg());
        verify(cardDataAssembler, never()).buildAndSign(any(), any(), anyString(), any(), any(), any());
    }

    @Test
    void 日票不可用返8004并带回对端文案() {
        givenUserInfo(CARD_TYPE_ONE_DAY, "07", null);
        when(dailyTicketClient.checkRideAvailability(CARD_ID))
                .thenReturn(new RpcOutcome.BizRejected("8004", "日票已过期"));

        RequestIndustryDataResult response = orchestrator.requestIndustryData(onlineRequest(CARD_TYPE_ONE_DAY));

        assertEquals("8004", response.getRetCode());
        assertEquals("日票已过期", response.getRetMsg());
        verify(cardDataAssembler, never()).buildAndSign(any(), any(), anyString(), any(), any(), any());
    }


    @Test
    void 日票域不可达时降级放行() {
        givenUserInfo(CARD_TYPE_ONE_DAY, "07", null);
        givenQrStatus("01", "9");
        when(dailyTicketClient.checkRideAvailability(CARD_ID))
                .thenReturn(new RpcOutcome.Unreachable(new IllegalStateException("连接超时")));
        givenCardData("CARDDATA_OK");

        RequestIndustryDataResult response = orchestrator.requestIndustryData(onlineRequest(CARD_TYPE_ONE_DAY));

        assertEquals("0000", response.getRetCode());
        assertEquals("CARDDATA_OK", response.getCardData());
    }

    @Test
    void 在线码取码状态当前值生码() {
        givenUserInfo(CARD_TYPE_QR, "07", null);
        givenQrStatus("01", "9");
        givenCardData("CARDDATA_ONLINE");

        RequestIndustryDataResult response = orchestrator.requestIndustryData(onlineRequest());

        assertEquals("0000", response.getRetCode());
        assertEquals("CARDDATA_ONLINE", response.getCardData());
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> txnSeq = ArgumentCaptor.forClass(String.class);
        verify(cardDataAssembler).buildAndSign(any(), any(), anyString(), any(),
                status.capture(), txnSeq.capture());
        assertEquals("01", status.getValue());
        assertEquals("9", txnSeq.getValue());
    }

    @Test
    void 缺卡号返参数错误() {
        RequestIndustryDataReqDTO request = onlineRequest();
        request.setCardId(" ");

        RequestIndustryDataResult response = orchestrator.requestIndustryData(request);

        assertEquals("cardId不能为空", response.getRetMsg());
        verify(accountClient, never()).queryUserInfo(any());
    }


    @Test
    void 站外离线码进出两张共用同一个递增序列号() {
        givenUserInfo(CARD_TYPE_QR, "07", null);
        givenQrStatus("01", "9");
        when(cardDataAssembler.buildAndSign(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(cardData("ENTRY_DATA"), cardData("EXIT_DATA"));

        RequestNoSignalDataResult response = orchestrator.requestNoSignalData(offlineRequest());

        assertEquals("0000", response.getRetCode());
        assertEquals("ENTRY_DATA", response.getEntryData());
        assertEquals("EXIT_DATA", response.getExitData());
        assertEquals("07", response.getChannel());
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> txnSeq = ArgumentCaptor.forClass(String.class);
        verify(cardDataAssembler, times(2)).buildAndSign(any(), any(), anyString(), any(),
                status.capture(), txnSeq.capture());
        assertEquals("01", status.getAllValues().get(0), "进站码取码状态当前值");
        assertEquals("04", status.getAllValues().get(1), "出站码固定站内状态");
        assertEquals("10", txnSeq.getAllValues().get(0), "序列号 MUST +1");
        assertEquals("10", txnSeq.getAllValues().get(1), "两张共用同一个序列号");
    }

    @Test
    void 站内离线码只发出站码且不推进序列号() {
        givenUserInfo(CARD_TYPE_QR, "07", null);
        givenQrStatus("04", "9");
        givenCardData("EXIT_ONLY");

        RequestNoSignalDataResult response = orchestrator.requestNoSignalData(offlineRequest());

        assertEquals("0000", response.getRetCode());
        assertNull(response.getEntryData());
        assertEquals("EXIT_ONLY", response.getExitData());
        ArgumentCaptor<String> txnSeq = ArgumentCaptor.forClass(String.class);
        verify(cardDataAssembler, times(1)).buildAndSign(any(), any(), anyString(), any(),
                any(), txnSeq.capture());
        assertEquals("9", txnSeq.getValue());
    }

    @Test
    void 生码失败时透传下游错误() {
        givenUserInfo(CARD_TYPE_QR, "07", null);
        givenQrStatus("01", "9");
        IndustryCardDataBuildRespDTO failed = new IndustryCardDataBuildRespDTO();
        failed.setRetCode("8001");
        failed.setRetMsg("行业数据卡数据非法（期望 80 位十六进制），请检查签名段长度");
        when(cardDataAssembler.buildAndSign(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(failed);

        RequestIndustryDataResult response = orchestrator.requestIndustryData(onlineRequest());

        assertEquals("8001", response.getRetCode());
        assertEquals("行业数据卡数据非法（期望 80 位十六进制），请检查签名段长度", response.getRetMsg());
        assertNull(response.getCardData());
    }

    private void givenUserInfo(String cardType, String channel, String hceData) {
        QueryUserInfoResult userInfo = new QueryUserInfoResult();
        userInfo.setRetCode("0000");
        userInfo.setThirdUserId(THIRD_USER_ID);
        userInfo.setCardId(CARD_ID);
        userInfo.setCardType(cardType);
        userInfo.setChannel(channel);
        userInfo.setHceData(hceData);
        userInfo.setCardIssueCode("0700");
        when(accountClient.queryUserInfo(any(QueryUserInfoReqDTO.class))).thenReturn(userInfo);
    }

    private void givenQrStatus(String status, String txnSeq) {
        QueryStatusRespDTO qrStatus = new QueryStatusRespDTO();
        qrStatus.setRetCode("0000");
        qrStatus.setStatus(status);
        qrStatus.setTxnSeq(txnSeq);
        when(agmRideStatusService.queryQrCodeStatus(any(QueryStatusReqDTO.class))).thenReturn(qrStatus);
    }

    private void givenCardData(String cardData) {
        when(cardDataAssembler.buildAndSign(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(cardData(cardData));
    }

    private IndustryCardDataBuildRespDTO cardData(String cardData) {
        IndustryCardDataBuildRespDTO response = new IndustryCardDataBuildRespDTO();
        response.setRetCode("0000");
        response.setRetMsg("成功");
        response.setCardData(cardData);
        return response;
    }

    private RequestIndustryDataReqDTO onlineRequest() {
        return onlineRequest(CARD_TYPE_QR);
    }

    private RequestIndustryDataReqDTO onlineRequest(String cardType) {
        RequestIndustryDataReqDTO request = new RequestIndustryDataReqDTO();
        request.setThirdUserId(THIRD_USER_ID);
        request.setCardId(CARD_ID);
        request.setCardType(cardType);
        return request;
    }

    private RequestNoSignalDataReqDTO offlineRequest() {
        RequestNoSignalDataReqDTO request = new RequestNoSignalDataReqDTO();
        request.setThirdUserId(THIRD_USER_ID);
        request.setCardId(CARD_ID);
        request.setCardType(CARD_TYPE_QR);
        return request;
    }
}
