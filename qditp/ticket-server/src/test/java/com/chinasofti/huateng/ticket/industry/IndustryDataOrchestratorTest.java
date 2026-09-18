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

/**
 * 行业数据编排的特征测试：钉住 2026-09-17 从 `fep-app-server` 迁进来之前的对外行为（ADR-D142）。
 *
 * <p>迁移是**纯重构**，因此这些断言同时是「迁移正确」的判据：任何一条变红都 MUST 先确认
 * 是不是有意的契约变更，NEVER 直接改断言迁就实现。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IndustryDataOrchestratorTest {

    private static final String THIRD_USER_ID = "0700009918";
    private static final String CARD_ID = "0426090949000384";
    /** 二维码后付费，走完整生码链路。 */
    private static final String CARD_TYPE_QR = "0441";
    /** HCE 后付费，短路整条生码。 */
    private static final String CARD_TYPE_HCE = "0442";
    /** 一日票，触发日票族前置校验。 */
    private static final String CARD_TYPE_ONE_DAY = "0445";
    /**
     * 合法的 HCE 缓存卡数据：MUST 是 64 位以上的十六进制串。
     *
     * <p>实现侧对它有长度 + 字符集校验（不只判非空），因此这里 NEVER 用「HCEDATA0001」这类
     * 看着像数据的假串 —— 那会被判成格式非法、走到错误分支上。
     */
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

    /** HCE 卡直接返缓存卡数据，NEVER 出网生码。 */
    @Test
    void hce卡短路生码直接返缓存卡数据() {
        givenUserInfo(CARD_TYPE_HCE, "07", HCE_CARD_DATA);

        RequestIndustryDataResult response = orchestrator.requestIndustryData(onlineRequest());

        assertEquals("0000", response.getRetCode());
        assertEquals(HCE_CARD_DATA, response.getCardData());
        verify(cardDataAssembler, never()).buildAndSign(any(), any(), anyString(), any(), any(), any());
        verify(agmRideStatusService, never()).queryQrCodeStatus(any());
    }

    /** HCE 卡但库里没存卡数据时 MUST 报错，NEVER 返空 cardData 的成功。 */
    @Test
    void hce卡缺卡数据时报错() {
        givenUserInfo(CARD_TYPE_HCE, "07", null);

        RequestIndustryDataResult response = orchestrator.requestIndustryData(onlineRequest());

        assertEquals("HCE卡数据不存在", response.getRetMsg());
        assertNull(response.getCardData());
    }

    /**
     * HCE 卡数据长度或字符集不合法时同样拒绝：它是直接发给闸机的码体，
     * MUST NOT 把一段短串或含非十六进制字符的值当成卡数据返出去。
     */
    @Test
    void hce卡数据格式非法时报错() {
        givenUserInfo(CARD_TYPE_HCE, "07", "HCEDATA0001");

        RequestIndustryDataResult response = orchestrator.requestIndustryData(onlineRequest());

        assertEquals("HCE卡数据不存在", response.getRetMsg());
        assertNull(response.getCardData());
    }

    /** 签约渠道为空是 8001，且 NEVER 继续生码（码体的签约渠道位无从填）。 */
    @Test
    void 签约渠道为空返8001() {
        givenUserInfo(CARD_TYPE_QR, null, null);

        RequestIndustryDataResult response = orchestrator.requestIndustryData(onlineRequest());

        assertEquals("8001", response.getRetCode());
        assertEquals("用户签约渠道不能为空", response.getRetMsg());
        verify(cardDataAssembler, never()).buildAndSign(any(), any(), anyString(), any(), any(), any());
    }

    /** 日票不可用时拒发乘车码，返 8004 + 原样带回日票域的文案。 */
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

    /**
     * 日票域不可达 MUST 降级放行（闸机侧 {@code GateDailyTicketCoordinator} 还有一道权威校验），
     * NEVER 因为一次网络抖动就拒发乘车码。
     */
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

    /** 在线码的票卡状态与交易序列号 MUST 取码状态表当前值，NEVER 自行推进。 */
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

    /** 三项必填缺一即 INVALID_PARAM，NEVER 打到下游。 */
    @Test
    void 缺卡号返参数错误() {
        RequestIndustryDataReqDTO request = onlineRequest();
        request.setCardId(" ");

        RequestIndustryDataResult response = orchestrator.requestIndustryData(request);

        assertEquals("cardId不能为空", response.getRetMsg());
        verify(accountClient, never()).queryUserInfo(any());
    }

    /**
     * 站外离线码一次发两张，**两张共用同一个 +1 后的序列号**：它们属于即将发生的同一笔行程，
     * NEVER 给出站码再 +1。
     */
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

    /** 已在站内时只需要出站码，序列号沿用当前值（出站与进站是同一笔交易）。 */
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

    /** 生码失败 MUST 原样带回下游的码与文案，NEVER 兜成成功。 */
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
