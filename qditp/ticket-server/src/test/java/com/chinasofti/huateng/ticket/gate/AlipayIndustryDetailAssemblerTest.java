package com.chinasofti.huateng.ticket.gate;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.model.app.RequestStationLineInfoResult;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.ticket.entrytxn.EntryTxnQueryService;
import com.chinasofti.huateng.ticket.station.StationLineResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class AlipayIndustryDetailAssemblerTest {

    private EntryTxnQueryService entryTxnQueryService;
    private StationLineResolver stationLineResolver;
    private AlipayIndustryDetailAssembler assembler;

    @BeforeEach
    void setUp() {
        entryTxnQueryService = mock(EntryTxnQueryService.class);
        stationLineResolver = mock(StationLineResolver.class);
        Executor directExecutor = Runnable::run;
        assembler = new AlipayIndustryDetailAssembler(entryTxnQueryService, stationLineResolver,
                "0007", directExecutor);
        when(stationLineResolver.resolveLineInfo(anyString())).thenReturn(null);
    }

    private NotifyVerifyResultReqDTO normalExitRequest() {
        NotifyVerifyResultReqDTO request = new NotifyVerifyResultReqDTO();
        request.setCardId("0426091000000013");
        request.setCardType("0441");
        request.setPhone("13800138000");
        request.setItpUserId("2088ALIPAYUSER");
        request.setTrxType("02");
        request.setTicketTransSeq("T20261117000001");
        request.setHandleStationCode("0230");
        request.setHandleDateTime("20261117093000");
        request.setLastHandleStationCode("0110");
        request.setLastHandleDateTime("20261117090000");
        request.setDeviceId("AGM023001");
        request.setIssueChannelCode("07");
        return request;
    }

    @Test
    @DisplayName("V1.2 必填键补齐：cardType/phone/ticketTransSeq/ticketFlag/cumulativeType 齐落值")
    void v12RequiredKeysPresent() {
        JSONObject detail = JSON.parseObject(assembler.assemble(normalExitRequest()));

        assertEquals("0441", detail.getString("cardType"));
        assertEquals("13800138000", detail.getString("phone"));
        assertEquals("T20261117000001", detail.getString("ticketTransSeq"), "MUST 用文档键名");
        assertEquals("T20261117000001", detail.getString("tikcetTransSeq"), "MUST 保留甲方规范样例拼写，双键同值");
        assertEquals("N", detail.getString("ticketFlag"), "companionFlag 为空 = 普通票 N");
        assertEquals("01", detail.getString("cumulativeType"), "trxType=02 普通出站 = 01");
        assertEquals("0007", detail.getString("cardIssueCode"), "支付宝渠道取 alipay.trip.card-issue-code 配置");
        assertEquals(0, detail.getIntValue("overTimeAmount"), "非超时出站的超时费为 0（数字类型，NEVER 带引号）");
    }

    @Test
    @DisplayName("非支付宝发行渠道（APP/TVM/BOM）：cardIssueCode 取报文 issueChannelCode 左补零到 4 位，NEVER 恒写 0007")
    void nonAlipayCardIssueCodeComesFromRequest() {
        NotifyVerifyResultReqDTO request = normalExitRequest();
        request.setIssueChannelCode("01");

        JSONObject detail = JSON.parseObject(assembler.assemble(request));

        assertEquals("0001", detail.getString("cardIssueCode"), "APP 渠道 01 → 0001（与支付宝 0007 同形）");
    }

    @Test
    @DisplayName("发卡渠道代码缺报文：空串兜底（NEVER 留 null——fastjson2 丢 null 键对端看不到）")
    void blankCardIssueCodeWhenIssueChannelMissing() {
        NotifyVerifyResultReqDTO request = normalExitRequest();
        request.setIssueChannelCode(null);

        JSONObject detail = JSON.parseObject(assembler.assemble(request));

        assertEquals("", detail.getString("cardIssueCode"));
    }

    @Test
    @DisplayName("超时出站：cumulativeType=02，且 NEVER 再出现 fineAmount 旧键名")
    void overtimeExitMapping() {
        NotifyVerifyResultReqDTO request = normalExitRequest();
        request.setTrxType("03");
        request.setOvertimeAmount("300");

        JSONObject detail = JSON.parseObject(assembler.assemble(request));

        assertEquals("02", detail.getString("cumulativeType"), "trxType=03 超时出站 = 02");
        assertEquals(300, detail.getIntValue("overTimeAmount"), "单位分、int 类型出网");
        assertFalse(detail.containsKey("fineAmount"), "旧键名 MUST 下线，对端只认 overTimeAmount");
    }

    @Test
    @DisplayName("同行票/第三方票：ticketFlag 透传，cumulativeType=03 不参与累计")
    void companionTicketMapping() {
        NotifyVerifyResultReqDTO request = normalExitRequest();
        request.setCompanionFlag("Y");
        JSONObject detail = JSON.parseObject(assembler.assemble(request));
        assertEquals("Y", detail.getString("ticketFlag"));
        assertEquals("03", detail.getString("cumulativeType"), "同行票优先于超时判定（文档：03-其他不参与累计的票）");

        request.setCompanionFlag("C");
        detail = JSON.parseObject(assembler.assemble(request));
        assertEquals("C", detail.getString("ticketFlag"), "其他合作方票 C 透传");
        assertEquals("03", detail.getString("cumulativeType"));
    }

    @Test
    @DisplayName("手机号缺失：phone 置空串、整单 MUST NOT 失败（与 entryDeviceCode 同口径）")
    void missingPhoneFallsBackToBlank() {
        NotifyVerifyResultReqDTO request = normalExitRequest();
        request.setPhone(null);

        JSONObject detail = JSON.parseObject(assembler.assemble(request));

        assertEquals("", detail.getString("phone"));
        assertEquals("0426091000000013", detail.getString("cardNum"), "其余键不受影响");
    }

    @Test
    @DisplayName("超时金额非数字：overTimeAmount 缺键 + 其余键不受影响")
    void nonNumericOverTimeAmountOmitted() {
        NotifyVerifyResultReqDTO request = normalExitRequest();
        request.setTrxType("03");
        request.setOvertimeAmount("abc");

        JSONObject detail = JSON.parseObject(assembler.assemble(request));

        assertFalse(detail.containsKey("overTimeAmount"), "错误值 MUST NOT 出网，缺失优于错值");
        assertEquals("02", detail.getString("cumulativeType"));
    }

    @Test
    @DisplayName("stationLineResolver 命中：线路名称进报文；欠费时按站点码兜底")
    void lineInfoEnrichmentAndFallback() {
        RequestStationLineInfoResult line = new RequestStationLineInfoResult();
        line.setLineCode("02");
        line.setLineName("2号线");
        line.setStationName("五四广场");
        when(stationLineResolver.resolveLineInfo("0230")).thenReturn(line);

        JSONObject detail = JSON.parseObject(assembler.assemble(normalExitRequest()));

        assertEquals("02", detail.getString("exitLineCode"));
        assertEquals("2号线", detail.getString("exitLineName"));
        assertEquals("五四广场", detail.getString("exitStationName"));
        assertEquals("0110", detail.getString("entryStationName"), "无线路信息时按站点码兜底");
    }

    @Test
    @DisplayName("payTriggerType 不在本类落值：由 gate-txn-pay 发送侧按入口注入")
    void payTriggerTypeNotAssembledHere() {
        JSONObject detail = JSON.parseObject(assembler.assemble(normalExitRequest()));
        assertFalse(detail.containsKey("payTriggerType"),
                "快照是出站时固定的，首次/补偿只有发起方知道，组装器写死会让补偿重推错标 01");
    }

    @Test
    @DisplayName("进站设备查询异常：entryDeviceCode 置空、整单不失败")
    void entryDeviceQueryFailureTolerated() {
        when(entryTxnQueryService.queryEntryDevice(anyString()))
                .thenThrow(new RuntimeException("connect timed out"));

        JSONObject detail = JSON.parseObject(assembler.assemble(normalExitRequest()));

        assertEquals("", detail.getString("entryDeviceCode"));
        assertNull(detail.getString("payTriggerType"));
    }
}
