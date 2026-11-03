package com.chinasofti.huateng.account.service.impl;

import com.chinasofti.huateng.account.entity.UserAccEmployeeCard;
import com.chinasofti.huateng.account.mapper.AccountExceptionTicketMapper;
import com.chinasofti.huateng.account.mapper.UserAccEmployeeCardMapper;
import com.chinasofti.huateng.account.service.EmployeeCardOutboundService;
import com.chinasofti.huateng.account.service.EmployeeCardPersistenceService;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 员工码信息查询测试。
 *
 * <p>重点覆盖：注销（{@code CARD_STATUS=4}）后资料字段被清空，查询时 MUST NOT
 * 再调 ACC 补全，否则会把已注销员工的敏感资料重新写回本地表。</p>
 */
class EmployeeCardQueryTest {

    private static final String CARD_NO = "13111111111";

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
    }

    @Test
    @DisplayName("正常态资料缺失：调 ACC 补全并回写")
    void queryNormalFillsProfileFromAcc() {
        givenCard(1, null);
        when(outboundService.queryFromAcc(CARD_NO)).thenReturn(accCard("一一一", "公司"));
        doAnswer(invocation -> {
            UserAccEmployeeCard target = invocation.getArgument(0);
            EmployeeCardInfoDTO source = invocation.getArgument(1);
            target.setEmployeeName(source.getEmployeeName());
            target.setCompany(source.getCompany());
            return null;
        }).when(persistenceService).refreshProfileFromAcc(any(), any());

        EmployeeCardQueryResult result = service.queryEmployeeCard(queryRequest());

        assertEquals("0000", result.getRetCode());
        assertEquals("一一一", result.getEmployeeName());
        verify(outboundService).queryFromAcc(CARD_NO);
        verify(persistenceService).refreshProfileFromAcc(any(), any());
    }

    @Test
    @DisplayName("注销态资料缺失：禁止调 ACC 补全，直接返回当前记录")
    void queryCanceledDoesNotFillProfile() {
        givenCard(4, null);

        EmployeeCardQueryResult result = service.queryEmployeeCard(queryRequest());

        assertEquals("0000", result.getRetCode());
        assertEquals(4, result.getCardStatus());
        assertNull(result.getEmployeeName());
        verify(outboundService, never()).queryFromAcc(anyString());
        verify(persistenceService, never()).refreshProfileFromAcc(any(), any());
    }

    @Test
    @DisplayName("注销态资料仍存在：不补全，原样返回")
    void queryCanceledWithProfileReturnsAsIs() {
        givenCard(4, "一一一");

        EmployeeCardQueryResult result = service.queryEmployeeCard(queryRequest());

        assertEquals("0000", result.getRetCode());
        assertEquals(4, result.getCardStatus());
        assertEquals("一一一", result.getEmployeeName());
        verify(outboundService, never()).queryFromAcc(anyString());
        verify(persistenceService, never()).refreshProfileFromAcc(any(), any());
    }

    private void givenCard(int status, String employeeName) {
        UserAccEmployeeCard card = new UserAccEmployeeCard();
        card.setCardNo(CARD_NO);
        card.setPhone(CARD_NO);
        card.setCardStatus(status);
        card.setEmployeeName(employeeName);
        when(employeeCardMapper.selectByCardNo(CARD_NO)).thenReturn(card);
    }

    private EmployeeCardInfoDTO accCard(String employeeName, String company) {
        EmployeeCardInfoDTO dto = new EmployeeCardInfoDTO();
        dto.setCardNo(CARD_NO);
        dto.setEmployeeName(employeeName);
        dto.setCompany(company);
        return dto;
    }

    private EmployeeCardQueryReqDTO queryRequest() {
        EmployeeCardQueryReqDTO request = new EmployeeCardQueryReqDTO();
        request.setCardNo(CARD_NO);
        return request;
    }
}
