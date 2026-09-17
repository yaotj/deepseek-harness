package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** IF1A-01 日票协同器特征测试。 */
class GateDailyTicketCoordinatorTest {

    private static final String DAILY_TICKET_CARD_TYPE = "0445";
    private static final String QR_CARD_TYPE = "0441";

    private DailyTicketClient dailyTicketClient;
    private GateDailyTicketCoordinator coordinator;

    @BeforeEach
    void setUp() {
        dailyTicketClient = mock(DailyTicketClient.class);
        coordinator = new GateDailyTicketCoordinator();
        ReflectionTestUtils.setField(coordinator, "dailyTicketClient", dailyTicketClient);
    }

    private NotifyVerifyResultReqDTO request(String trxType) {
        NotifyVerifyResultReqDTO request = new NotifyVerifyResultReqDTO();
        request.setCardId("0426091000000013");
        request.setTrxType(trxType);
        request.setSignChannelCode("12");
        request.setHandleStationCode("2002");
        request.setLastHandleStationCode("1001");
        return request;
    }

    private DailyTicketBaseResult baseResult(String retCode, String retMsg) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        result.setRetCode(retCode);
        result.setRetMsg(retMsg);
        return result;
    }

    @Test
    @DisplayName("进站校验只对「进站 + 日票」发起远端调用")
    void 进站校验只对进站加日票发起远端调用() {
        for (String nonEntry : new String[]{"02", "03", "04", "99"}) {
            assertTrue(coordinator.checkEntryAllowed(request(nonEntry), new NotifyVerifyResultRespDTO(),
                    DAILY_TICKET_CARD_TYPE), nonEntry + " 不是进站交易，MUST 直接放过");
        }
        assertTrue(coordinator.checkEntryAllowed(request("01"), new NotifyVerifyResultRespDTO(), QR_CARD_TYPE),
                "非日票卡种不问日票服务");
        verifyNoInteractions(dailyTicketClient);
    }

    @Test
    @DisplayName("trxType 白名单缺失会把乘客困在付费区（钉住分流本身）")
    void 出站交易MUST不走进站校验() {
        when(dailyTicketClient.entryCheck(anyString())).thenReturn(baseResult("8001", "次数已用尽"));
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();

        assertTrue(coordinator.checkEntryAllowed(request("02"), response, DAILY_TICKET_CARD_TYPE),
                "出站 MUST NOT 被进站校验拦下：计次票用尽时出站必然返非 0000，"
                        + "拦下则不开门、明细不落库、状态卡在已进站，乘客困在付费区");
        assertNull(response.getRetCode());
        verify(dailyTicketClient, never()).entryCheck(anyString());
    }

    @Test
    @DisplayName("进站校验通过则继续检票流程")
    void 进站校验通过则继续() {
        when(dailyTicketClient.entryCheck("0426091000000013")).thenReturn(baseResult("0000", "成功"));
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();

        assertTrue(coordinator.checkEntryAllowed(request("01"), response, DAILY_TICKET_CARD_TYPE));
        assertNull(response.getRetCode(), "通过时 MUST NOT 动应答");
    }

    @Test
    @DisplayName("进站校验被业务拒绝：填 QR_CODE_NOT_FOUND 并透传对方 retMsg")
    void 进站校验被业务拒绝时填错误码并透传retMsg() {
        when(dailyTicketClient.entryCheck(anyString())).thenReturn(baseResult("8001", "日票次数已用尽"));
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();

        assertFalse(coordinator.checkEntryAllowed(request("01"), response, DAILY_TICKET_CARD_TYPE));
        assertEquals("8004", response.getRetCode(), "确定性拒绝用 QR_CODE_NOT_FOUND");
        assertEquals("日票次数已用尽", response.getRetMsg(), "MUST 透传对方原因，NEVER 换成自己的话");
    }

    @Test
    @DisplayName("进站校验服务不可达 MUST 抛异常，NEVER 转成确定性业务码")
    void 进站校验不可达MUST抛异常() {
        when(dailyTicketClient.entryCheck(anyString())).thenThrow(new RuntimeException("connect timed out"));
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> coordinator.checkEntryAllowed(request("01"), response, DAILY_TICKET_CARD_TYPE));
        assertTrue(thrown.getMessage().contains("日票进站校验服务不可用"));
        assertNull(response.getRetCode(),
                "MUST NOT 就地转业务码：转了闸机就不重试，一次抖动把正常票判死");
    }

    @Test
    @DisplayName("进站校验响应解析为空同样抛异常（与不可达同一口径）")
    void 进站校验响应为空同样抛异常() {
        when(dailyTicketClient.entryCheck(anyString())).thenReturn(null);
        assertThrows(RuntimeException.class, () -> coordinator.checkEntryAllowed(
                request("01"), new NotifyVerifyResultRespDTO(), DAILY_TICKET_CARD_TYPE));
    }

    @Test
    @DisplayName("出站扣次只在 trxType=02 且日票时触发（03 超时出站现状不扣次）")
    void 出站扣次只在02且日票时触发() {
        for (String nonExit : new String[]{"01", "03", "04", "99"}) {
            coordinator.markUsedOnExit(request(nonExit), DAILY_TICKET_CARD_TYPE);
        }
        coordinator.markUsedOnExit(request("02"), QR_CARD_TYPE);
        verifyNoInteractions(dailyTicketClient);
    }

    @Test
    @DisplayName("出站扣次入参：orderNo 传 null，进站站与出站站不可颠倒")
    void 出站扣次入参顺序() {
        when(dailyTicketClient.markUsed(anyString(), isNull(), isNull(), anyString(), anyString()))
                .thenReturn(baseResult("0000", "成功"));

        coordinator.markUsedOnExit(request("02"), DAILY_TICKET_CARD_TYPE);

        verify(dailyTicketClient, times(1)).markUsed(
                eq("0426091000000013"), isNull(), isNull(),
                eq("1001"), eq("2002"));
    }

    @Test
    @DisplayName("出站扣次失败 MUST 放行：返非 0000 不抛异常")
    void 出站扣次业务失败MUST不抛() {
        when(dailyTicketClient.markUsed(anyString(), any(), any(), any(), any()))
                .thenReturn(baseResult("8001", "次数不足"));
        coordinator.markUsedOnExit(request("02"), DAILY_TICKET_CARD_TYPE);
    }

    @Test
    @DisplayName("出站扣次失败 MUST 放行：响应为空不抛异常")
    void 出站扣次响应为空MUST不抛() {
        when(dailyTicketClient.markUsed(anyString(), any(), any(), any(), any())).thenReturn(null);
        coordinator.markUsedOnExit(request("02"), DAILY_TICKET_CARD_TYPE);
    }

    @Test
    @DisplayName("出站扣次失败 MUST 放行：RPC 抛异常也要吞掉，否则应答 0000 走不到")
    void 出站扣次抛异常MUST吞掉() {
        when(dailyTicketClient.markUsed(anyString(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("connect timed out"));
        coordinator.markUsedOnExit(request("02"), DAILY_TICKET_CARD_TYPE);
    }

    @Test
    @DisplayName("countingFlag / countingTimes 只由卡种推导，恒有值")
    void 两个计次字段只由卡种推导且恒有值() {
        for (String dailyTicket : new String[]{"0445", "0446", "0447", "0448"}) {
            assertEquals(1, coordinator.resolveCountingTimes(dailyTicket), dailyTicket + " 一趟消耗 1 次");
            assertEquals("Y", coordinator.resolveCountingFlag(dailyTicket), "记期票也是 Y，NEVER 退回 1/2");
        }
        for (String notDaily : new String[]{"0441", "0442", "0443", "0444", null}) {
            assertEquals(0, coordinator.resolveCountingTimes(notDaily), "非日票 MUST 是 0，NEVER null");
            assertEquals("N", coordinator.resolveCountingFlag(notDaily), "非日票 MUST 是 N，NEVER null");
        }
        verifyNoInteractions(dailyTicketClient);
    }

    @Test
    @DisplayName("票号查询成功只填 ticketCode 一个字段")
    void 票号查询成功只填ticketCode() {
        QueryDailyTicketInfoResult info = new QueryDailyTicketInfoResult();
        info.setRetCode("0000");
        info.setTicketCode("DT20260914000001");
        info.setActualTimes(-99);
        when(dailyTicketClient.queryDailyTicketInfo(any())).thenReturn(info);

        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();
        coordinator.applyDailyTicketFields(request("02"), response);

        assertEquals("DT20260914000001", response.getTicketCode());
        assertNull(response.getCountingTimes(),
                "本方法 MUST NOT 赋 countingTimes——它由 resolveCountingTimes 在本方法之前赋好");
        assertNull(response.getCountingFlag(), "同上，NEVER 把这两个字段挪进本方法的成功分支");
    }

    @Test
    @DisplayName("票号查询失败只降级 ticketCode，不抛异常、不影响其余字段")
    void 票号查询失败只降级ticketCode() {
        QueryDailyTicketInfoResult rejected = new QueryDailyTicketInfoResult();
        rejected.setRetCode("8001");
        when(dailyTicketClient.queryDailyTicketInfo(any())).thenReturn(rejected);
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();
        coordinator.applyDailyTicketFields(request("02"), response);
        assertNull(response.getTicketCode());

        when(dailyTicketClient.queryDailyTicketInfo(any())).thenReturn(null);
        coordinator.applyDailyTicketFields(request("02"), response);
        assertNull(response.getTicketCode());

        when(dailyTicketClient.queryDailyTicketInfo(any())).thenThrow(new RuntimeException("connect timed out"));
        coordinator.applyDailyTicketFields(request("02"), response);
        assertNull(response.getTicketCode(), "三种失败形态都只降级、都不抛");
    }

    @Test
    @DisplayName("票号查询挂掉时两个计次字段仍有值（2026-09-10 修过的缺陷形状）")
    void 票号查询挂掉时两个计次字段仍有值() {
        when(dailyTicketClient.queryDailyTicketInfo(any())).thenThrow(new RuntimeException("connect timed out"));

        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();
        response.setCountingTimes(coordinator.resolveCountingTimes(DAILY_TICKET_CARD_TYPE));
        response.setCountingFlag(coordinator.resolveCountingFlag(DAILY_TICKET_CARD_TYPE));
        coordinator.applyDailyTicketFields(request("02"), response);

        assertEquals(1, response.getCountingTimes(), "远端挂掉不影响这两个字段");
        assertEquals("Y", response.getCountingFlag());
        assertNull(response.getTicketCode());
    }

    @Test
    @DisplayName("三套失败口径互不相同：同一个 RPC 异常在三处的结论必须不一样")
    void 同一个RPC异常在三处的结论必须不一样() {
        RuntimeException timeout = new RuntimeException("connect timed out");
        when(dailyTicketClient.entryCheck(anyString())).thenThrow(timeout);
        when(dailyTicketClient.markUsed(anyString(), any(), any(), any(), any())).thenThrow(timeout);
        when(dailyTicketClient.queryDailyTicketInfo(any())).thenThrow(timeout);

        assertThrows(RuntimeException.class, () -> coordinator.checkEntryAllowed(
                request("01"), new NotifyVerifyResultRespDTO(), DAILY_TICKET_CARD_TYPE),
                "进站校验 MUST 抛：可重试语义");
        coordinator.markUsedOnExit(request("02"), DAILY_TICKET_CARD_TYPE);
        coordinator.applyDailyTicketFields(request("02"), new NotifyVerifyResultRespDTO());
    }
}
