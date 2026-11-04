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

    /** IF5A-03 审计键 = {@code cardId:U+optDate}（ADR-D173），同卡不同 optDate 各落一行。 */
    @Test
    void updateAuditOrderNoComposedWithCardIdAndOptDate() {
        when(ticketClient.requestUpdateCardData(any())).thenReturn(updateResp("0000", "成功"));

        service.requestUpdateCardData(updateRequest("0426090949000058", "20261104161126"));

        ArgumentCaptor<F2fResultReport> captor = ArgumentCaptor.forClass(F2fResultReport.class);
        verify(reportMapper).insert(captor.capture());
        assertEquals("0426090949000058:U20261104161126", captor.getValue().getOrderNo());
        assertEquals("BOM_BIZ_RESULT", captor.getValue().getReportType());
    }

    /** 同一 {@code (cardId, optDate)} 重试撞唯一索引被幂等吞掉，下游真实结果（8001）照常返回。 */
    @Test
    void updateAuditDuplicateSwallowedAndDownstreamResultPreserved() {
        when(ticketClient.requestUpdateCardData(any())).thenReturn(updateResp("8001", "票卡状态不允许此操作"));
        doThrow(duplicateKey()).when(reportMapper).insert(any());

        com.alibaba.fastjson2.JSONObject body =
                service.requestUpdateCardData(updateRequest("0426090949000058", "20261104161126"));

        assertEquals("8001", body.getString("retCode"));
    }

    /** IF5A-09 首报：审计键 = {@code cardId:N+timestamp} 且触发 account-server 回写。 */
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

    /** IF5A-09 重复通知：撞唯一索引 → 幂等返回成功，NEVER 再调回写。 */
    @Test
    void hceNotiDuplicateSkipsWriteBack() {
        doThrow(duplicateKey()).when(reportMapper).insert(any());

        com.alibaba.fastjson2.JSONObject body =
                service.receiveHceUpdateResult(hceNoti("0426090949000058", "20261104161127", "AABBCCDD"));

        assertEquals("0000", body.getString("retCode"));
        verify(accountClient, never()).updateHceData(any());
    }

    /** 更新与紧随通知同秒（事件键相同）时，流前缀 {@code U}/{@code N} 保证两条链路审计键互不挤占。 */
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
