package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.api.device.bom.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.facepay.mapper.F2fResultReportMapper;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 锁死 IF5A-01 往下游透传的 {@code bomStationCode}。
 *
 * <p>该字段是 ticket-server 判「同站免费更新 / 跨站付费更新」的唯一输入（ADR-D157）：
 * 传错站会把跨站误判成同站而少收费，传空会把已确认的跨站降级成未知站。故这里从公开入口
 * 断言实际发出去的报文，而不是断言某个私有方法的返回值——后者照不到「忘了调」这类改动。
 *
 * <p>每个用例只调用一次被测方法：{@code verify} 默认只认一次调用，写两遍会因调用次数不符而失败。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class F2fHceServiceTest {

    @Mock
    private TicketClient ticketClient;

    @Mock
    private AccountClient accountClient;

    @Mock
    private F2fResultReportMapper reportMapper;

    @InjectMocks
    private F2fHceService service;

    private static RequestCardDataAnalyseReqDTO request(String deviceId) {
        RequestCardDataAnalyseReqDTO request = new RequestCardDataAnalyseReqDTO();
        request.setProviderId("03");
        request.setMsisdn("13900000000");
        request.setCardId("3100000000000001");
        request.setUpdateType("00");
        request.setDeviceId(deviceId);
        return request;
    }

    /** 走一次公开入口，返回实际发给下游的 {@code bomStationCode}。 */
    private String bomStationCodeSentFor(String deviceId) {
        RequestCardDataAnalyseRespDTO response = new RequestCardDataAnalyseRespDTO();
        response.setRetCode("0000");
        when(ticketClient.requestCardDataAnalyse(any())).thenReturn(response);

        service.requestCardDataAnalyse(request(deviceId));

        ArgumentCaptor<com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO> captor =
                ArgumentCaptor.forClass(com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO.class);
        verify(ticketClient).requestCardDataAnalyse(captor.capture());
        return captor.getValue().getBomStationCode();
    }

    @Test
    void passesFirstFourCharsOfBomDeviceId() {
        assertEquals("0622", bomStationCodeSentFor("06220801"));
    }

    @Test
    void passesFirstFourCharsOfTvmStyleDeviceId() {
        assertEquals("0352", bomStationCodeSentFor("03520801"));
    }

    /** 推不出站码时保留本字段约定的空串语义（下游按未知站处理），NEVER 变成 null 或臆造的站。 */
    @Test
    void sendsEmptyStringWhenDeviceIdIsTooShort() {
        assertEquals("", bomStationCodeSentFor("062"));
    }

    @Test
    void sendsEmptyStringWhenDeviceIdIsAbsent() {
        assertEquals("", bomStationCodeSentFor(null));
    }
}
