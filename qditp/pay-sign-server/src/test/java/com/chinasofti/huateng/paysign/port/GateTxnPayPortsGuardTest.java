package com.chinasofti.huateng.paysign.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 护栏：闸机域两个出向端口的应答判读（2026-09-17，ADR-D119）。
 *
 * <p>这两个 adapter 是 pay-sign-server 里 GateTxnPayClient 的唯一持有者。
 * 判读写错的后果两边都不对称，且都不抛异常：
 * <ul>
 *   <li><b>扣费状态收敛</b>——把「对端拒绝」判成成功，本笔就被当作已收口，
 *       支付中心不再重推，而闸机域的 DEBIT_STATUS 永远停在中间态。</li>
 *   <li><b>未结清欠费</b>——把「问不出来」判成「没欠费」，解约就会放行，
 *       <b>用户欠着钱把签约解掉、这笔钱再也扣不到</b>，接口还返 0000。</li>
 * </ul>
 */
class GateTxnPayPortsGuardTest {

    private static final String ORDER = "GT20260917000000001";
    private static final String USER = "U-TEST-0001";
    private static final String ALIPAY_VENDOR = "03";

    private final GateTxnPayClient client = mock(GateTxnPayClient.class);
    private final DebitSyncPort debitSyncPort = new DebitSyncRpcAdapter(client);
    private final UnsettledOrderPort unsettledOrderPort = new UnsettledOrderRpcAdapter(client);

    // ---------------- DebitSyncPort ----------------

    /** 成功码 ⇒ Ok，且三个入参原样装进闸机域报文（remark 由调用点给，NEVER 在 adapter 里写死）。 */
    @Test
    void syncSuccessIsOkAndPassesAllThreeFields() {
        GateTxnPayRespDTO response = new GateTxnPayRespDTO();
        response.setRetCode("0000");
        when(client.syncDebitStatus(any(GateTxnPaySyncStatusReqDTO.class))).thenReturn(response);

        RpcOutcome outcome = debitSyncPort.syncDebitStatus(ORDER, "SUCCESS", "支付结果回调");

        assertTrue(outcome.isOk());
        ArgumentCaptor<GateTxnPaySyncStatusReqDTO> captor =
                ArgumentCaptor.forClass(GateTxnPaySyncStatusReqDTO.class);
        verify(client).syncDebitStatus(captor.capture());
        assertEquals(ORDER, captor.getValue().getOrderNo());
        assertEquals("SUCCESS", captor.getValue().getPayStatus());
        assertEquals("支付结果回调", captor.getValue().getRemark());
    }

    /** 非成功码 ⇒ BizRejected，且把对端码与文案带出来（调用点要打进日志供人工核对）。 */
    @Test
    void syncNonSuccessCodeIsBizRejectedWithRemoteCode() {
        GateTxnPayRespDTO response = new GateTxnPayRespDTO();
        response.setRetCode("9999");
        response.setRetMsg("订单不存在");
        when(client.syncDebitStatus(any(GateTxnPaySyncStatusReqDTO.class))).thenReturn(response);

        RpcOutcome outcome = debitSyncPort.syncDebitStatus(ORDER, "SUCCESS", "支付结果回调");

        RpcOutcome.BizRejected rejected = assertInstanceOf(RpcOutcome.BizRejected.class, outcome);
        assertEquals("9999", rejected.retCode());
        assertEquals("订单不存在", rejected.retMsg());
    }

    /** 响应体为空 ⇒ BizRejected（对端答了 HTTP 但没给业务码），NEVER 判成 Ok。 */
    @Test
    void syncNullResponseIsRejectedNeverOk() {
        when(client.syncDebitStatus(any(GateTxnPaySyncStatusReqDTO.class))).thenReturn(null);

        RpcOutcome outcome = debitSyncPort.syncDebitStatus(ORDER, "SUCCESS", "支付结果回调");

        assertInstanceOf(RpcOutcome.BizRejected.class, outcome);
    }

    /** 抛异常 ⇒ Unreachable 且带上原始 cause，异常 NEVER 穿出端口。 */
    @Test
    void syncExceptionIsUnreachableAndCarriesCause() {
        when(client.syncDebitStatus(any(GateTxnPaySyncStatusReqDTO.class)))
                .thenThrow(new RuntimeException("connection reset"));

        RpcOutcome outcome = debitSyncPort.syncDebitStatus(ORDER, "SUCCESS", "支付结果回调");

        RpcOutcome.Unreachable unreachable = assertInstanceOf(RpcOutcome.Unreachable.class, outcome);
        assertNotNull(unreachable.cause());
    }

    // ---------------- UnsettledOrderPort ----------------

    /** 成功码 + 有欠费 ⇒ Answered(true)。 */
    @Test
    void unsettledAnsweredTrue() {
        when(client.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrder("0000", true));

        UnsettledOrderAnswer answer = unsettledOrderPort.hasUnsettledOrder(USER, ALIPAY_VENDOR, null);

        assertEquals(new UnsettledOrderAnswer.Answered(true), answer);
    }

    /** 成功码 + 无欠费 ⇒ Answered(false)：这一条才是允许放行解约的唯一形态。 */
    @Test
    void unsettledAnsweredFalse() {
        when(client.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrder("0000", false));

        UnsettledOrderAnswer answer = unsettledOrderPort.hasUnsettledOrder(USER, ALIPAY_VENDOR, null);

        assertEquals(new UnsettledOrderAnswer.Answered(false), answer);
    }

    /**
     * **本批的核心护栏**：闸机域答了但不是成功码 ⇒ Rejected，
     * <b>NEVER 变成 Answered(false)</b>。
     *
     * <p>注意桩里 hasFailedOrder 是 false —— 收口前 TerminationInternalServiceImpl 正是取了这个
     * 默认值当答案，于是「查询失败」被翻译成「该用户没欠费」并放行解约。
     */
    @Test
    void unsettledNonSuccessCodeIsRejectedNeverAnsweredFalse() {
        when(client.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrder("9999", false));

        UnsettledOrderAnswer answer = unsettledOrderPort.hasUnsettledOrder(USER, ALIPAY_VENDOR, null);

        UnsettledOrderAnswer.Rejected rejected =
                assertInstanceOf(UnsettledOrderAnswer.Rejected.class, answer);
        assertEquals("9999", rejected.retCode());
    }

    /** 响应体为空 ⇒ Rejected。 */
    @Test
    void unsettledNullResponseIsRejected() {
        when(client.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class))).thenReturn(null);

        assertInstanceOf(UnsettledOrderAnswer.Rejected.class,
                unsettledOrderPort.hasUnsettledOrder(USER, ALIPAY_VENDOR, null));
    }

    /** 抛异常 ⇒ Unknown，异常 NEVER 穿出端口。 */
    @Test
    void unsettledExceptionIsUnknown() {
        when(client.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenThrow(new RuntimeException("connect timed out"));

        UnsettledOrderAnswer answer = unsettledOrderPort.hasUnsettledOrder(USER, ALIPAY_VENDOR, null);

        assertNotNull(assertInstanceOf(UnsettledOrderAnswer.Unknown.class, answer).cause());
    }

    /**
     * {@code requestTime} MUST 原样透传：闸机域按它筛选统计窗口，
     * 漏传等于查了另一个时间范围，答案照样是 0000、只是查错了。
     */
    @Test
    void unsettledRequestTimeIsPassedThrough() {
        LocalDateTime requestTime = LocalDateTime.of(2026, 9, 17, 10, 30, 0);
        when(client.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrder("0000", false));

        unsettledOrderPort.hasUnsettledOrder(USER, ALIPAY_VENDOR, requestTime);

        ArgumentCaptor<GateTxnPayFailedOrderReqDTO> captor =
                ArgumentCaptor.forClass(GateTxnPayFailedOrderReqDTO.class);
        verify(client).hasFailedOrder(captor.capture());
        assertEquals(USER, captor.getValue().getThirdUserId());
        assertEquals(ALIPAY_VENDOR, captor.getValue().getPaymentVendor());
        assertEquals(requestTime, captor.getValue().getRequestTime());
    }

    private GateTxnPayFailedOrderRespDTO failedOrder(String resultCode, boolean hasFailedOrder) {
        GateTxnPayFailedOrderRespDTO response = new GateTxnPayFailedOrderRespDTO();
        response.setResultCode(resultCode);
        response.setHasFailedOrder(hasFailedOrder);
        return response;
    }
}
