package com.chinasofti.huateng.account.service.impl;

import com.chinasofti.huateng.account.domain.EmployeeCardStatus;
import com.chinasofti.huateng.account.entity.UserAccEmployeeCard;
import com.chinasofti.huateng.account.mapper.AccountExceptionTicketMapper;
import com.chinasofti.huateng.account.mapper.UserAccEmployeeCardMapper;
import com.chinasofti.huateng.account.service.EmployeeCardOutboundService;
import com.chinasofti.huateng.account.service.EmployeeCardPersistenceService;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.employee.EmployeeCardActivateReqDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 员工卡激活 / 禁用的前置状态白名单测试（IT-044：补上 {@code 2 → 1} 重新启用）。
 *
 * <p>白名单只有两条激活边：{@code 3 → 1}（首次激活）、{@code 2 → 1}（停用后重新启用）；
 * {@code 1}（已启用）与 {@code 4}（注销终态）MUST 拒绝。禁用只允许 {@code 1 → 2}。</p>
 */
class EmployeeCardActivateWhitelistTest {

    private static final String CARD_NO = "13045027231";
    private static final String ACC_OK = "{\"retCode\":\"0000\",\"retMsg\":\"ok\"}";

    private UserAccEmployeeCardMapper employeeCardMapper;
    private AccountExceptionTicketMapper accountExceptionTicketMapper;
    private EmployeeCardPersistenceService persistenceService;
    private EmployeeCardOutboundService outboundService;
    private EmployeeCardServiceImpl service;

    @BeforeEach
    void setUp() {
        employeeCardMapper = mock(UserAccEmployeeCardMapper.class);
        accountExceptionTicketMapper = mock(AccountExceptionTicketMapper.class);
        persistenceService = mock(EmployeeCardPersistenceService.class);
        outboundService = mock(EmployeeCardOutboundService.class);
        service = new EmployeeCardServiceImpl(employeeCardMapper, accountExceptionTicketMapper,
                persistenceService, outboundService);
        when(outboundService.isActivationUrlConfigured()).thenReturn(true);
        when(outboundService.requestActivation(any())).thenReturn(ACC_OK);
        when(persistenceService.applyActivationResult(anyString(), anyInt(), anyBoolean(), anyString()))
                .thenReturn(true);
    }

    @Test
    @DisplayName("激活白名单：3 未激活、2 禁用可激活；1 已启用、4 注销、null 不可")
    void activatableFromWhitelist() {
        assertTrue(EmployeeCardStatus.activatableFrom(3), "3 未激活 → 1 首次激活");
        assertTrue(EmployeeCardStatus.activatableFrom(2), "2 禁用 → 1 重新启用（IT-044）");
        assertFalse(EmployeeCardStatus.activatableFrom(1), "1 已启用无需激活");
        assertFalse(EmployeeCardStatus.activatableFrom(4), "4 注销为终态，NEVER 复活");
        assertFalse(EmployeeCardStatus.activatableFrom(null), "null 不可放行");
    }

    @Test
    @DisplayName("3 → 1 首次激活：调 ACC 并回写状态 1")
    void activateFromNotEnabled() {
        givenCardStatus(3);

        CommonResult result = service.activateEmployeeCard(request(1));

        assertEquals("0000", result.getRetCode());
        verify(outboundService).requestActivation(any());
        verify(persistenceService).applyActivationResult(eq(CARD_NO), eq(1), eq(true), eq("APP请求激活员工码成功"));
    }

    @Test
    @DisplayName("2 → 1 停用后重新启用：调 ACC 并回写状态 1（IT-044 修复点）")
    void activateFromDisabled() {
        givenCardStatus(2);

        CommonResult result = service.activateEmployeeCard(request(1));

        assertEquals("0000", result.getRetCode());
        verify(outboundService).requestActivation(any());
        verify(persistenceService).applyActivationResult(eq(CARD_NO), eq(1), eq(true), eq("APP请求激活员工码成功"));
    }

    @Test
    @DisplayName("1 → 1 已启用再激活：2002 且不调 ACC")
    void activateFromNormalRejected() {
        givenCardStatus(1);

        CommonResult result = service.activateEmployeeCard(request(1));

        assertEquals("2002", result.getRetCode());
        verify(outboundService, never()).requestActivation(any());
    }

    @Test
    @DisplayName("4 → 1 已注销再激活：2002（终态不可复活）")
    void activateFromCanceledRejected() {
        givenCardStatus(4);

        CommonResult result = service.activateEmployeeCard(request(1));

        assertEquals("2002", result.getRetCode());
        verify(outboundService, never()).requestActivation(any());
    }

    @Test
    @DisplayName("禁用只允许 1 → 2：状态 2 时 2002")
    void disableFromDisabledRejected() {
        givenCardStatus(2);

        CommonResult result = service.activateEmployeeCard(request(0));

        assertEquals("2002", result.getRetCode());
        verify(outboundService, never()).requestActivation(any());
    }

    @Test
    @DisplayName("1 → 2 禁用：调 ACC 并回写状态 2")
    void disableFromNormal() {
        givenCardStatus(1);

        CommonResult result = service.activateEmployeeCard(request(0));

        assertEquals("0000", result.getRetCode());
        verify(outboundService).requestActivation(any());
        verify(persistenceService).applyActivationResult(eq(CARD_NO), eq(2), eq(false), eq("APP请求禁用员工码成功"));
    }

    private void givenCardStatus(int status) {
        UserAccEmployeeCard card = new UserAccEmployeeCard();
        card.setCardNo(CARD_NO);
        card.setCardStatus(status);
        when(employeeCardMapper.selectByCardNo(CARD_NO)).thenReturn(card);
    }

    private EmployeeCardActivateReqDTO request(int actionFlag) {
        EmployeeCardActivateReqDTO request = new EmployeeCardActivateReqDTO();
        request.setCardNo(CARD_NO);
        request.setActionFlag(actionFlag);
        return request;
    }
}
