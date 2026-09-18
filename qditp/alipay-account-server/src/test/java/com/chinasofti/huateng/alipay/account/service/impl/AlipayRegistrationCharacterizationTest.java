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

/**
 * 钉住支付宝开卡四步链路除 confirm 之外的口径（ADR-D143）。
 *
 * <p>与 {@link AlipayAccountCardPoolConfirmTest} 正交：那个类只管第 4 步 confirm，
 * 这个类管顺序、幂等短路、三种预占失败分流、注册失败处置与 `businessId` 键格式。
 * 键格式那条**尤其 NEVER 删**：它是发号的幂等边界，退回旧式 `前缀:用户:票种` 拼接后，
 * 含冒号的 `thirdUserId` 会与另一组入参撞出同一个键 —— 两个用户抢同一张卡，
 * 而这在真实环境里既造不出也看不见。
 */
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

    /**
     * 四步顺序 NEVER 变：预占 → 注册乘车状态 → 落两表 → confirm。
     * 顺序一乱就会出现「卡号还没确认就发给用户」或「乘车状态先建、开户失败」的不一致。
     */
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

    /**
     * 已开户即幂等短路：返 0000 带库里那张卡，四步一步都不走。
     * 重试因此 NEVER 补 confirm —— 首次 confirm 失败留下的预占只能等 sys_job 107 回收。
     */
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

    /**
     * 卡池空是「稍后重试有用」，返 9999；票种被拒是「重试无用」，返 8001；调不通返 9001。
     * 三者压成同一个错误码会让上游把不可恢复的配置错误一直重推。
     */
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

    /**
     * 注册乘车状态失败：两表 NEVER 落、confirm NEVER 发、预占 NEVER release。
     */
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

    /**
     * `businessId` 是长度前缀键，且 reserve 与 confirm 必须用同一个值 ——
     * 两处不一致时 confirm 会被卡池按「预占不存在」拒掉。
     */
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

    /**
     * HCE 卡与员工票在入参校验就被挡掉：两者都不走卡池发号。
     */
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
