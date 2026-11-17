package com.chinasofti.huateng.alipay.account.service.impl;

import com.chinasofti.huateng.alipay.account.entity.AlipayUserInfo;
import com.chinasofti.huateng.alipay.account.mapper.AlipayRegLogMapper;
import com.chinasofti.huateng.alipay.account.mapper.AlipayUserInfoMapper;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolActionResult;
import com.chinasofti.huateng.model.cardpool.CardPoolOutcome;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationRespDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReserveResult;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import com.chinasofti.huateng.rpc.cardpool.CardPoolClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class AlipayRegistrationCharacterizationTest {

    private static final String THIRD_USER_ID = "0700009999";
    private static final String APP_CARD_TYPE = "02";
    private static final String ISSUE_CARD_TYPE = "0441";
    private static final String CARD_NO = "0426090949009999";
    private static final String RESERVATION_ID = "11111111-2222-3333-4444-555555555555";

    private AlipayUserInfoMapper userInfoMapper;
    private AlipayRegLogMapper regLogMapper;
    private TicketClient ticketClient;
    private CardPoolClient cardPoolClient;
    private AlipayRegistrationService service;

    @BeforeEach
    void setUp() {
        userInfoMapper = mock(AlipayUserInfoMapper.class);
        regLogMapper = mock(AlipayRegLogMapper.class);
        ticketClient = mock(TicketClient.class);
        cardPoolClient = mock(CardPoolClient.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            invocation.<Consumer<Object>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service = new AlipayRegistrationService(userInfoMapper, regLogMapper, ticketClient,
                cardPoolClient, transactionTemplate);

        when(userInfoMapper.selectByThirdUserId(anyString())).thenReturn(null);
        when(cardPoolClient.reserve(any())).thenReturn(CardPoolReserveResult.success(reservation()));
        when(ticketClient.registerRideStatus(any())).thenReturn(registerResp(FepAppErrorCodeEnum.SUCCESS.getCode()));
        when(cardPoolClient.confirm(anyString(), anyString())).thenReturn(CardPoolActionResult.success());
    }

    private CardPoolReservationRespDTO reservation() {
        CardPoolReservationRespDTO reservation = new CardPoolReservationRespDTO();
        reservation.setReservationId(RESERVATION_ID);
        reservation.setCardNo(CARD_NO);
        reservation.setCardType(ISSUE_CARD_TYPE);
        return reservation;
    }

    private RegisterRideStatusRespDTO registerResp(String retCode) {
        RegisterRideStatusRespDTO response = new RegisterRideStatusRespDTO();
        response.setRetCode(retCode);
        return response;
    }

    private AlipayTripRequestApplicationReqDTO request() {
        return request(APP_CARD_TYPE);
    }

    private AlipayTripRequestApplicationReqDTO request(String cardType) {
        AlipayTripRequestApplicationReqDTO request = new AlipayTripRequestApplicationReqDTO();
        request.setThirdUserId(THIRD_USER_ID);
        request.setCardType(cardType);
        request.setCardIssueCode("0007");
        return request;
    }


    @Test
    void shouldRunFourStepsInFixedOrder() {
        AlipayTripRequestApplicationRespDTO response = service.requestApplication(request());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        InOrder order = inOrder(cardPoolClient, ticketClient, userInfoMapper, regLogMapper);
        order.verify(cardPoolClient).reserve(any());
        order.verify(ticketClient).registerRideStatus(any());
        order.verify(userInfoMapper).insert(any());
        order.verify(regLogMapper).insert(any());
        order.verify(cardPoolClient).confirm(anyString(), anyString());
    }


    @Test
    void existingUserShouldShortCircuitBeforeAnyStep() {
        AlipayUserInfo existed = new AlipayUserInfo();
        existed.setThirdUserId(THIRD_USER_ID);
        existed.setCardId(CARD_NO);
        existed.setCardType(APP_CARD_TYPE);
        existed.setStatus("ACTIVE");
        when(userInfoMapper.selectByThirdUserId(THIRD_USER_ID)).thenReturn(existed);

        AlipayTripRequestApplicationRespDTO response = service.requestApplication(request());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        assertEquals("用户已开户", response.getRetMsg());
        assertEquals(CARD_NO, response.getCardId());
        verify(cardPoolClient, never()).reserve(any());
        verify(ticketClient, never()).registerRideStatus(any());
        verify(userInfoMapper, never()).insert(any());
        verify(cardPoolClient, never()).confirm(anyString(), anyString());
    }


    @Test
    void reserveFailureShouldMapToDistinctCodes() {
        when(cardPoolClient.reserve(any()))
                .thenReturn(CardPoolReserveResult.failure(CardPoolOutcome.POOL_EMPTY, "无可用卡号"));
        assertEquals(FepAppErrorCodeEnum.FAIL.getCode(), service.requestApplication(request()).getRetCode());

        when(cardPoolClient.reserve(any()))
                .thenReturn(CardPoolReserveResult.failure(CardPoolOutcome.REJECTED, "票种不走卡池"));
        assertEquals(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), service.requestApplication(request()).getRetCode());

        when(cardPoolClient.reserve(any()))
                .thenReturn(CardPoolReserveResult.failure(CardPoolOutcome.CALL_FAILED, "连接超时"));
        assertEquals(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), service.requestApplication(request()).getRetCode());

        verify(userInfoMapper, never()).insert(any());
        verify(cardPoolClient, never()).release(anyString(), anyString());
    }


    @Test
    void registerRideStatusFailureShouldNotPersistOrConfirm() {
        when(ticketClient.registerRideStatus(any())).thenReturn(registerResp("9999"));

        AlipayTripRequestApplicationRespDTO response = service.requestApplication(request());

        assertEquals(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), response.getRetCode());
        verify(userInfoMapper, never()).insert(any());
        verify(regLogMapper, never()).insert(any());
        verify(cardPoolClient, never()).confirm(anyString(), anyString());
        verify(cardPoolClient, never()).release(anyString(), anyString());
    }


    @Test
    void reservationBusinessIdShouldBeLengthPrefixedAndReusedOnConfirm() {
        service.requestApplication(request());

        ArgumentCaptor<CardPoolReservationReqDTO> captor =
                ArgumentCaptor.forClass(CardPoolReservationReqDTO.class);
        verify(cardPoolClient).reserve(captor.capture());
        String expectedKey = "ALIPAY_ACCOUNT_OPEN:" + THIRD_USER_ID.length() + ":" + THIRD_USER_ID
                + ":" + ISSUE_CARD_TYPE;
        assertEquals(expectedKey, captor.getValue().getBusinessId());
        assertEquals("ALIPAY_ACCOUNT_OPEN", captor.getValue().getBusinessType());
        assertEquals(ISSUE_CARD_TYPE, captor.getValue().getCardType());
        assertEquals(THIRD_USER_ID, captor.getValue().getOwnerId());
        verify(cardPoolClient).confirm(eq(RESERVATION_ID), eq(expectedKey));
    }


    @Test
    void hceAndEmployeeCardShouldBeRejectedBeforeReserve() {
        AlipayTripRequestApplicationRespDTO hce = service.requestApplication(request("03"));
        assertEquals(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), hce.getRetCode());
        assertEquals("HCE卡仅支持安全服务实时发卡", hce.getRetMsg());

        AlipayTripRequestApplicationRespDTO employee = service.requestApplication(request("11"));
        assertEquals(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), employee.getRetCode());
        assertEquals("员工票仅支持APP静默开户", employee.getRetMsg());

        verify(cardPoolClient, never()).reserve(any());
    }
}
