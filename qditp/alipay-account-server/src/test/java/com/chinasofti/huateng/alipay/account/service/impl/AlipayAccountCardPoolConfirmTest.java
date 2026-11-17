package com.chinasofti.huateng.alipay.account.service.impl;

import com.chinasofti.huateng.alipay.account.mapper.AlipayRegLogMapper;
import com.chinasofti.huateng.alipay.account.mapper.AlipayUserInfoMapper;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolActionResult;
import com.chinasofti.huateng.model.cardpool.CardPoolOutcome;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationRespDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReserveResult;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import com.chinasofti.huateng.rpc.cardpool.CardPoolClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class AlipayAccountCardPoolConfirmTest {

    private static final String THIRD_USER_ID = "0700009999";
    private static final String APP_CARD_TYPE = "02";
    private static final String CARD_NO = "0426090949009999";
    private static final String RESERVATION_ID = "11111111-2222-3333-4444-555555555555";

    private AlipayUserInfoMapper userInfoMapper;
    private AlipayRegLogMapper regLogMapper;
    private TicketClient ticketClient;
    private CardPoolClient cardPoolClient;
    private AlipayRegistrationService service;

    @BeforeEach
    void setUp() throws Exception {
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
        CardPoolReservationRespDTO reservation = new CardPoolReservationRespDTO();
        reservation.setReservationId(RESERVATION_ID);
        reservation.setCardNo(CARD_NO);
        reservation.setCardType("0441");
        when(cardPoolClient.reserve(any())).thenReturn(CardPoolReserveResult.success(reservation));
        RegisterRideStatusRespDTO registerResp = new RegisterRideStatusRespDTO();
        registerResp.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        when(ticketClient.registerRideStatus(any())).thenReturn(registerResp);
    }

    private AlipayTripRequestApplicationReqDTO request() {
        AlipayTripRequestApplicationReqDTO request = new AlipayTripRequestApplicationReqDTO();
        request.setThirdUserId(THIRD_USER_ID);
        request.setCardType(APP_CARD_TYPE);
        request.setCardIssueCode("0007");
        return request;
    }


    @Test
    void confirmRejectedShouldReturnFailureAndKeepRowsAndNotRelease() {
        when(cardPoolClient.confirm(anyString(), anyString()))
                .thenReturn(CardPoolActionResult.failure(CardPoolOutcome.REJECTED, "预占记录不存在或状态不允许确认"));

        AlipayTripRequestApplicationRespDTO response = service.requestApplication(request());

        assertEquals(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), response.getRetCode());
        assertEquals("卡号确认失败，请稍后重试", response.getRetMsg());
        verify(userInfoMapper, times(1)).insert(any());
        verify(regLogMapper, times(1)).insert(any());
        verify(cardPoolClient, never()).release(anyString(), anyString());
    }


    @Test
    void confirmSuccessShouldReturnCardIdAndRegisterWithAlipayChannel() {
        when(cardPoolClient.confirm(anyString(), anyString())).thenReturn(CardPoolActionResult.success());

        AlipayTripRequestApplicationRespDTO response = service.requestApplication(request());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        assertEquals(CARD_NO, response.getCardId());
        ArgumentCaptor<RegisterRideStatusReqDTO> captor = ArgumentCaptor.forClass(RegisterRideStatusReqDTO.class);
        verify(ticketClient).registerRideStatus(captor.capture());
        assertEquals("07", captor.getValue().getChannel());
        verify(cardPoolClient, never()).release(anyString(), anyString());
    }
}
