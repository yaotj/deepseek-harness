package com.chinasofti.huateng.cardpool.service.impl;

import com.chinasofti.huateng.cardpool.client.AccLogicNumClient;
import com.chinasofti.huateng.cardpool.config.CardPoolProperties;
import com.chinasofti.huateng.cardpool.entity.LogicCardPoolCard;
import com.chinasofti.huateng.cardpool.mapper.LogicCardPoolMapper;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationRespDTO;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预占 / 确认 / 释放三个动作的幂等与归属语义回归。
 *
 * <p>这三条链路的行为在测试环境已实测（并发 20 路取号无重号、同 businessId 并发只消耗一张、
 * confirm 与 release 重复调用均返回成功），本类把结论固定成断言，避免后续改动无声退化。</p>
 */
class CardPoolReservationTest {

    private LogicCardPoolMapper mapper;
    private CardPoolServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(LogicCardPoolMapper.class);
        service = new CardPoolServiceImpl(mapper, mock(AccLogicNumClient.class), mock(CardPoolProperties.class),
                noTracer());
    }

    /**
     * 单测不验证链路追踪，给一个「取不到 Tracer」的 provider 即可。
     *
     * <p>{@code getIfAvailable()} 默认返回 null，`CardPoolServiceImpl.withTraceContext` 会原样返回任务，
     * 与 tracing 关闭的环境行为一致。`ObjectProvider` 继承 `Iterable`、不是函数式接口，不能用 lambda。</p>
     */
    @SuppressWarnings("unchecked")
    private ObjectProvider<Tracer> noTracer() {
        return (ObjectProvider<Tracer>) mock(ObjectProvider.class);
    }

    private CardPoolReservationReqDTO request(String cardType, String businessId) {
        CardPoolReservationReqDTO request = new CardPoolReservationReqDTO();
        request.setCardType(cardType);
        request.setBusinessType("UNIT_TEST");
        request.setBusinessId(businessId);
        request.setOwnerId("owner-1");
        return request;
    }

    private LogicCardPoolCard card(String cardNo, String cardType, String status, String businessId) {
        LogicCardPoolCard card = new LogicCardPoolCard();
        card.setId(1L);
        card.setCardNo(cardNo);
        card.setCardType(cardType);
        card.setStatus(status);
        card.setBusinessType("UNIT_TEST");
        card.setBusinessId(businessId);
        card.setReservationId("rid-1");
        return card;
    }

    /**
     * 非卡池票种（0442 走安全服务发卡）直接拒绝，不查库；抛异常由 Controller 翻成 400，
     * 与「池空」区分开，避免调用方把配置错误误报成卡池耗尽。
     */
    @Test
    void reserveRejectsNonPoolTicketType() {
        assertThrows(IllegalArgumentException.class, () -> service.reserve(request("0442", "B-1")));
        verify(mapper, never()).selectAvailableCandidates(anyString(), anyInt());
    }

    /**
     * 业务归属缺失时拒绝，不查库。
     */
    @Test
    void reserveRejectsMissingBusinessKey() {
        CardPoolReservationReqDTO request = request("0445", null);
        assertThrows(IllegalArgumentException.class, () -> service.reserve(request));
        verify(mapper, never()).selectAvailableCandidates(anyString(), anyInt());
    }

    /**
     * 同 businessId 且卡号仍为 RESERVED 时复用原预占，不再取新号。
     */
    @Test
    void reserveReusesReservedRecord() {
        when(mapper.selectByBusiness("UNIT_TEST", "B-1"))
                .thenReturn(card("0426090945000001", "0445", "RESERVED", "B-1"));
        CardPoolReservationRespDTO response = service.reserve(request("0445", "B-1"));
        assertNotNull(response);
        assertEquals("0426090945000001", response.getCardNo());
        verify(mapper, never()).selectAvailableCandidates(anyString(), anyInt());
    }

    /**
     * 已确认（ASSIGNED）后重复预占仍返回同一卡号。
     *
     * <p>2026-09-09 E2E 实测到的缺陷：此前 ASSIGNED 不在复用白名单内，重复请求拿到 null，
     * 上游翻译成「无可分配逻辑卡号」，把重复请求误报成卡池耗尽。</p>
     */
    @Test
    void reserveReusesAssignedRecord() {
        when(mapper.selectByBusiness("UNIT_TEST", "B-2"))
                .thenReturn(card("0426090945000002", "0445", "ASSIGNED", "B-2"));
        CardPoolReservationRespDTO response = service.reserve(request("0445", "B-2"));
        assertNotNull(response);
        assertEquals("0426090945000002", response.getCardNo());
        verify(mapper, never()).selectAvailableCandidates(anyString(), anyInt());
    }

    /**
     * 同一业务归属已占用其它票种卡号时拒绝，避免串票种发卡。
     */
    @Test
    void reserveRejectsCrossTicketTypeOwnership() {
        when(mapper.selectByBusiness("UNIT_TEST", "B-3"))
                .thenReturn(card("0426090946000003", "0446", "RESERVED", "B-3"));
        assertThrows(IllegalStateException.class, () -> service.reserve(request("0445", "B-3")));
    }

    /**
     * 池内无可用卡号时返回 null，由上游决定降级方式。
     */
    @Test
    void reserveReturnsNullWhenPoolEmpty() {
        when(mapper.selectAvailableCandidates(anyString(), anyInt())).thenReturn(List.of());
        assertNull(service.reserve(request("0445", "B-4")));
    }

    /**
     * 正常取号：候选非空且条件 UPDATE 命中 1 行。
     */
    @Test
    void reserveTakesCandidateWhenAvailable() {
        when(mapper.selectAvailableCandidates(anyString(), anyInt()))
                .thenReturn(List.of(card("0426090945000005", "0445", "AVAILABLE", null)));
        when(mapper.reserve(anyLong(), anyString(), anyString(), anyString(), any(), any(LocalDateTime.class)))
                .thenReturn(1);
        CardPoolReservationRespDTO response = service.reserve(request("0445", "B-5"));
        assertNotNull(response);
        assertEquals("0426090945000005", response.getCardNo());
        assertNotNull(response.getReservationId());
    }

    /**
     * 并发下唯一归属索引冲突时回查已存在记录并复用，不把冲突当失败上抛。
     */
    @Test
    void reserveResolvesUniqueKeyRace() {
        when(mapper.selectByBusiness("UNIT_TEST", "B-6"))
                .thenReturn(null)
                .thenReturn(card("0426090945000006", "0445", "RESERVED", "B-6"));
        when(mapper.selectAvailableCandidates(anyString(), anyInt()))
                .thenReturn(List.of(card("0426090945000099", "0445", "AVAILABLE", null)));
        when(mapper.reserve(anyLong(), anyString(), anyString(), anyString(), any(), any(LocalDateTime.class)))
                .thenThrow(new DataIntegrityViolationException("unique key"));
        CardPoolReservationRespDTO response = service.reserve(request("0445", "B-6"));
        assertNotNull(response);
        assertEquals("0426090945000006", response.getCardNo());
    }

    /**
     * 唯一归属索引冲突<b>被包成 {@code RuntimeException}</b> 时同样要复用，不能上抛。
     *
     * <p>2026-09-14 线上并发开户实测（ADR-D53）：本模块开了 tracing，`MapperAspectToTrace` 当时会把
     * mapper 抛出的异常包一层 {@code new RuntimeException(e)}，于是上面那条 `catch` 按类型匹配不中，
     * `ORA-00001` 直接冒到全局处理器返 500，account-server 收到空响应体、把它翻成
     * 「8003 暂无卡数据资源」—— 池子里明明有卡。切面已改成原样抛出，本用例锁住
     * <b>即使将来又有谁包一层，也 MUST 按链路识别冲突</b>。</p>
     */
    @Test
    void reserveResolvesUniqueKeyRaceWhenExceptionWrapped() {
        when(mapper.selectByBusiness("UNIT_TEST", "B-6W"))
                .thenReturn(null)
                .thenReturn(card("0426090945000066", "0445", "RESERVED", "B-6W"));
        when(mapper.selectAvailableCandidates(anyString(), anyInt()))
                .thenReturn(List.of(card("0426090945000098", "0445", "AVAILABLE", null)));
        when(mapper.reserve(anyLong(), anyString(), anyString(), anyString(), any(), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException(new DataIntegrityViolationException("unique key")));
        CardPoolReservationRespDTO response = service.reserve(request("0445", "B-6W"));
        assertNotNull(response);
        assertEquals("0426090945000066", response.getCardNo());
    }

    /**
     * 非完整性冲突的异常 MUST 原样上抛，NEVER 被当成「并发冲突」吞掉后回查。
     *
     * <p>把 catch 放宽到 {@code RuntimeException} 的代价就是这条：若不判类型，
     * 字段超长 / 连接中断都会走进回查分支，回查为空时才抛，错误信息全变形。</p>
     */
    @Test
    void reserveRethrowsNonIntegrityFailure() {
        when(mapper.selectAvailableCandidates(anyString(), anyInt()))
                .thenReturn(List.of(card("0426090945000097", "0445", "AVAILABLE", null)));
        when(mapper.reserve(anyLong(), anyString(), anyString(), anyString(), any(), any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("connection closed"));
        assertThrows(IllegalStateException.class, () -> service.reserve(request("0445", "B-6X")));
        verify(mapper, never()).selectByReservation(anyString());
    }

    /**
     * 确认命中 1 行即成功。
     */
    @Test
    void confirmSucceedsWhenRowUpdated() {
        when(mapper.confirm("rid-1", "B-7")).thenReturn(1);
        assertTrue(service.confirm("rid-1", "B-7"));
    }

    /**
     * 重复确认：UPDATE 命中 0 行，但回查已是 ASSIGNED 且归属一致，按成功处理。
     */
    @Test
    void confirmIsIdempotentWhenAlreadyAssigned() {
        when(mapper.confirm("rid-1", "B-8")).thenReturn(0);
        when(mapper.selectByReservation("rid-1")).thenReturn(card("0426090945000008", "0445", "ASSIGNED", "B-8"));
        assertTrue(service.confirm("rid-1", "B-8"));
    }

    /**
     * 归属不一致时确认失败，不能靠 reservationId 单独放行。
     */
    @Test
    void confirmRejectsBusinessIdMismatch() {
        when(mapper.confirm("rid-1", "B-WRONG")).thenReturn(0);
        when(mapper.selectByReservation("rid-1")).thenReturn(card("0426090945000009", "0445", "ASSIGNED", "B-9"));
        assertFalse(service.confirm("rid-1", "B-WRONG"));
    }

    /**
     * 预占不存在时确认失败。
     */
    @Test
    void confirmFailsWhenReservationUnknown() {
        when(mapper.confirm(anyString(), anyString())).thenReturn(0);
        when(mapper.selectByReservation(anyString())).thenReturn(null);
        assertFalse(service.confirm("rid-none", "B-10"));
    }

    /**
     * 重复释放：UPDATE 命中 0 行，但回查已回到 AVAILABLE，按成功处理。
     */
    @Test
    void releaseIsIdempotentWhenAlreadyAvailable() {
        when(mapper.release("rid-1", "B-11")).thenReturn(0);
        when(mapper.selectByReservation("rid-1")).thenReturn(card("0426090945000011", "0445", "AVAILABLE", null));
        assertTrue(service.release("rid-1", "B-11"));
    }

    /**
     * 已确认的卡号不允许被释放，防止重复请求把已发出的卡号收回。
     */
    @Test
    void releaseRejectsAssignedCard() {
        when(mapper.release("rid-1", "B-12")).thenReturn(0);
        when(mapper.selectByReservation("rid-1")).thenReturn(card("0426090945000012", "0445", "ASSIGNED", "B-12"));
        assertFalse(service.release("rid-1", "B-12"));
    }

    /**
     * 入参缺失时确认与释放一律拒绝，不落库。
     */
    @Test
    void blankArgumentsRejected() {
        assertFalse(service.confirm(null, "B-13"));
        assertFalse(service.confirm("rid-1", " "));
        assertFalse(service.release(null, "B-13"));
        assertFalse(service.release("rid-1", " "));
        verify(mapper, never()).confirm(anyString(), anyString());
        verify(mapper, never()).release(anyString(), anyString());
    }
}
