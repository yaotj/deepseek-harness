package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.api.device.bom.NotiUpdateHceDataReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.facepay.entity.F2fResultReport;
import com.chinasofti.huateng.facepay.mapper.F2fResultReportMapper;
import com.chinasofti.huateng.model.app.UpdateHceDataResult;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;
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
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


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

    @Test
    void sendsEmptyStringWhenDeviceIdIsTooShort() {
        assertEquals("", bomStationCodeSentFor("062"));
    }

    @Test
    void sendsEmptyStringWhenDeviceIdIsAbsent() {
        assertEquals("", bomStationCodeSentFor(null));
    }

    private static RequestCardDataUpdateReqDTO updateRequest(String cardId, String optDate) {
        RequestCardDataUpdateReqDTO request = new RequestCardDataUpdateReqDTO();
        request.setCardId(cardId);
        request.setUpdateType("00");
        request.setAdviceOpt("020");
        request.setOperaterId("2222222222");
        request.setUpdateStationCode("0622");
        request.setOptDate(optDate);
        request.setTransAmount("0");
        request.setDeviceId("06220801");
        return request;
    }

    private static NotiUpdateHceDataReqDTO hceNoti(String cardId, String timestamp, String hceData) {
        NotiUpdateHceDataReqDTO request = new NotiUpdateHceDataReqDTO();
        request.setCardId(cardId);
        request.setTimestamp(timestamp);
        request.setHceData(hceData);
        request.setDeviceId("06220801");
        return request;
    }

    private static RequestCardDataUpdateRespDTO updateResp(String retCode, String retMsg) {
        RequestCardDataUpdateRespDTO response = new RequestCardDataUpdateRespDTO();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        return response;
    }

    private static DuplicateKeyException duplicateKey() {
        return new DuplicateKeyException("ORA-00001",
                new SQLIntegrityConstraintViolationException(
                        "ORA-00001: unique constraint (QDITP.UK_F2F_REPORT_IDEM) violated"));
    }

    @Test
    void updateAuditOrderNoComposedWithCardIdAndOptDate() {
        when(ticketClient.requestUpdateCardData(any())).thenReturn(updateResp("0000", "成功"));

        service.requestUpdateCardData(updateRequest("0426090949000058", "20261104161126"));

        ArgumentCaptor<F2fResultReport> captor = ArgumentCaptor.forClass(F2fResultReport.class);
        verify(reportMapper).insert(captor.capture());
        assertEquals("0426090949000058:U20261104161126", captor.getValue().getOrderNo());
        assertEquals("BOM_BIZ_RESULT", captor.getValue().getReportType());
    }

    @Test
    void updateAuditDuplicateSwallowedAndDownstreamResultPreserved() {
        when(ticketClient.requestUpdateCardData(any())).thenReturn(updateResp("8001", "票卡状态不允许此操作"));
        doThrow(duplicateKey()).when(reportMapper).insert(any());

        com.alibaba.fastjson2.JSONObject body =
                service.requestUpdateCardData(updateRequest("0426090949000058", "20261104161126"));

        assertEquals("8001", body.getString("retCode"));
    }

    @Test
    void hceNotiFirstReportWritesBackWithTimestampKeyedAudit() {
        UpdateHceDataResult result = new UpdateHceDataResult();
        result.setRetCode("0000");
        when(accountClient.updateHceData(any())).thenReturn(result);

        service.receiveHceUpdateResult(hceNoti("0426090949000058", "20261104161127", "AABBCCDD"));

        ArgumentCaptor<F2fResultReport> captor = ArgumentCaptor.forClass(F2fResultReport.class);
        verify(reportMapper).insert(captor.capture());
        assertEquals("0426090949000058:N20261104161127", captor.getValue().getOrderNo());
        verify(accountClient).updateHceData(any());
    }

    @Test
    void hceNotiDuplicateSkipsWriteBack() {
        doThrow(duplicateKey()).when(reportMapper).insert(any());

        com.alibaba.fastjson2.JSONObject body =
                service.receiveHceUpdateResult(hceNoti("0426090949000058", "20261104161127", "AABBCCDD"));

        assertEquals("0000", body.getString("retCode"));
        verify(accountClient, never()).updateHceData(any());
    }

    @Test
    void updateAndNotiShareEventKeyButNotIdemKey() {
        when(ticketClient.requestUpdateCardData(any())).thenReturn(updateResp("0000", "成功"));
        UpdateHceDataResult result = new UpdateHceDataResult();
        result.setRetCode("0000");
        when(accountClient.updateHceData(any())).thenReturn(result);

        service.requestUpdateCardData(updateRequest("0426090949000058", "20261104161126"));
        service.receiveHceUpdateResult(hceNoti("0426090949000058", "20261104161126", "AABBCCDD"));

        ArgumentCaptor<F2fResultReport> captor = ArgumentCaptor.forClass(F2fResultReport.class);
        verify(reportMapper, times(2)).insert(captor.capture());
        List<F2fResultReport> reports = captor.getAllValues();
        assertEquals("0426090949000058:U20261104161126", reports.get(0).getOrderNo());
        assertEquals("0426090949000058:N20261104161126", reports.get(1).getOrderNo());
        verify(accountClient).updateHceData(any());
    }
}
