package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.gate.QRCodeStatusStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** IF5A-01 票卡分析里用户信息补齐的特征测试：msisdn 两域回落、cardIssueDate 单一数据源。 */
class CardDataAnalyseHandlerTest {

    private static final String CARD_ID = "0426091000000013";

    private AccountClient accountClient;
    private AlipayAccountClient alipayAccountClient;
    private CardDataAnalyseHandler handler;

    @BeforeEach
    void setUp() {
        accountClient = mock(AccountClient.class);
        alipayAccountClient = mock(AlipayAccountClient.class);

        QRCodeStatusStore store = mock(QRCodeStatusStore.class);
        QRCodeStatus status = new QRCodeStatus();
        status.setCodeStatus(QRCodeStatusEnum.ENTRY.getCode());
        when(store.findByCardId(anyString())).thenReturn(status);

        SupplementStateRules stateRules = mock(SupplementStateRules.class);
        when(stateRules.resolveCodeStatus(anyString())).thenReturn(QRCodeStatusEnum.ENTRY);
        when(stateRules.unknownStationCode()).thenReturn(SupplementCodec.STATION_UNKNOWN);
        when(stateRules.isUnknownStation(anyString())).thenReturn(true);
        when(stateRules.resolveAdviceOpt(any(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(List.of("000"));

        handler = new CardDataAnalyseHandler();
        ReflectionTestUtils.setField(handler, "qrCodeStatusStore", store);
        ReflectionTestUtils.setField(handler, "stationLineResolver", mock(
                com.chinasofti.huateng.ticket.station.StationLineResolver.class));
        ReflectionTestUtils.setField(handler, "accountClient", accountClient);
        ReflectionTestUtils.setField(handler, "alipayAccountClient", alipayAccountClient);
        ReflectionTestUtils.setField(handler, "stateRules", stateRules);
        ReflectionTestUtils.setField(handler, "fareQuery", mock(SupplementFareQuery.class));
    }

    /** BOM 上送的 providerId 恒为设备编码 03，与发卡渠道无关。 */
    private RequestCardDataAnalyseReqDTO bomRequest() {
        RequestCardDataAnalyseReqDTO request = new RequestCardDataAnalyseReqDTO();
        request.setCardId(CARD_ID);
        request.setProviderId("03");
        request.setUpdateType(SupplementCodec.UPDATE_TYPE_FREE_AREA);
        return request;
    }

    private QueryUserInfoResult accountHit(String msisdn, String regTms) {
        QueryUserInfoResult result = new QueryUserInfoResult();
        result.setRetCode("0000");
        result.setThirdUserId("00000123");
        result.setMsisdn(msisdn);
        result.setRegTms(regTms);
        return result;
    }

    private AlipayUserInfoDTO alipayHit(String phone) {
        AlipayUserInfoDTO user = new AlipayUserInfoDTO();
        user.setCardId(CARD_ID);
        user.setThirdUserId("2088ALIPAYUSER");
        user.setPhone(phone);
        return user;
    }

    private RequestCardDataAnalyseRespDTO handle(RequestCardDataAnalyseReqDTO request) {
        return handler.handle(request, new RequestCardDataAnalyseRespDTO());
    }

    @Test
    @DisplayName("account 域命中且有手机号：不回落支付宝")
    void account域有手机号时不回落支付宝() {
        when(accountClient.queryCardTypeByCardId(CARD_ID)).thenReturn(accountHit("13800000001", "20260101"));

        RequestCardDataAnalyseRespDTO response = handle(bomRequest());

        assertEquals("13800000001", response.getMsisdn());
        assertEquals("20260101", response.getCardIssueDate());
        verifyNoInteractions(alipayAccountClient);
    }

    @Test
    @DisplayName("account 域查不到该卡：按 cardId 回落支付宝账户域补 msisdn")
    void account域查不到时按卡号回落支付宝() {
        when(accountClient.queryCardTypeByCardId(CARD_ID)).thenReturn(null);
        when(alipayAccountClient.selectByCardId(CARD_ID)).thenReturn(alipayHit("13900000002"));

        RequestCardDataAnalyseRespDTO response = handle(bomRequest());

        assertEquals("13900000002", response.getMsisdn(), "判据是卡在哪个域查得到，NEVER 看报文 providerId");
        assertEquals("", response.getCardIssueDate(), "支付宝账户域没有发卡日期字段，只能留空串");
        verify(alipayAccountClient).selectByCardId(CARD_ID);
    }

    @Test
    @DisplayName("account 域命中但无手机号：仍回落支付宝，cardIssueDate 保留 account 域的值")
    void account域无手机号时回落支付宝但保留发卡日期() {
        when(accountClient.queryCardTypeByCardId(CARD_ID)).thenReturn(accountHit(null, "20260101"));
        when(alipayAccountClient.selectByCardId(CARD_ID)).thenReturn(alipayHit("13900000003"));

        RequestCardDataAnalyseRespDTO response = handle(bomRequest());

        assertEquals("13900000003", response.getMsisdn());
        assertEquals("20260101", response.getCardIssueDate());
    }

    @Test
    @DisplayName("两个域都查不到手机号：保留 BOM 上送值，不置空")
    void bothDomainsMissKeepBomMsisdn() {
        when(accountClient.queryCardTypeByCardId(CARD_ID)).thenReturn(null);
        when(alipayAccountClient.selectByCardId(CARD_ID)).thenReturn(null);
        RequestCardDataAnalyseReqDTO request = bomRequest();
        request.setMsisdn("13700000004");

        RequestCardDataAnalyseRespDTO response = handle(request);

        assertEquals("13700000004", response.getMsisdn());
    }

    @Test
    @DisplayName("支付宝域抛异常：整笔仍成功，msisdn 退化成 BOM 上送值")
    void alipayDomainThrowsDoesNotBreakResponse() {
        when(accountClient.queryCardTypeByCardId(CARD_ID)).thenReturn(null);
        when(alipayAccountClient.selectByCardId(CARD_ID)).thenThrow(new RuntimeException("connection reset"));
        RequestCardDataAnalyseReqDTO request = bomRequest();
        request.setMsisdn("13700000005");

        RequestCardDataAnalyseRespDTO response = handle(request);

        assertEquals(SupplementCodec.RET_SUCCESS, response.getRetCode());
        assertEquals("13700000005", response.getMsisdn());
    }
}
