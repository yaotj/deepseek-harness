package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayRetCode;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.notify.GateTxnPayNoticeService;
import com.chinasofti.huateng.gatetxnpay.writer.GateTxnPayWriter;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 补款收敛（{@code convergeDebitStatusForSupplement}）的并发语义锁。
 *
 * <p>核心约束：UPDATE 返 0 行时 MUST 重读真实状态再判断。
 * 原实现拿「UPDATE 之前的快照」判断，并发下（近 10 分钟自动重试或另一张补款单刚把它改成 SUCCESS）
 * 会把「重复支付」错报成 `STATUS_REJECT 订单状态不允许收敛：RETRY`，
 * 补款侧据此把明细标成「原订单收敛失败」而不是「重复支付待退款」——该退的钱可能就此不退。
 */
@ExtendWith(MockitoExtension.class)
class SupplementConvergeConcurrencyTest {

    private static final String ORIG_ORDER = "GT20261120140204397000126";
    private static final String TXN_DATE = "20261120";

    @Mock private GateTxnPayMapper gateTxnPayMapper;
    @Mock private GateTxnPayWriter gateTxnPayWriter;
    @Mock private GateTxnPayNoticeService gateTxnPayNoticeService;

    private GateTxnPayServiceImpl service;

    @BeforeEach
    void setUp() {
        // 本用例只走补款收敛分支，其余协作者不会被执行到，传 null 即可（构造器只做字段赋值）。
        service = new GateTxnPayServiceImpl(gateTxnPayMapper, gateTxnPayWriter, null, null,
                null, null, null, gateTxnPayNoticeService, null);
    }

    @Test
    void snapshotStaleButRowIsAlreadySuccessTreatedAsDuplicatePayment() {
        // 快照 RETRY（UPDATE 前的读）→ UPDATE 0 行 → 重读已是 SUCCESS（被自动重试抢走）
        when(gateTxnPayMapper.selectByOrderNo(ORIG_ORDER))
                .thenReturn(order("RETRY"))
                .thenReturn(order("SUCCESS"));
        when(gateTxnPayWriter.convergeDebitStatusForSupplement(anyString(), anyString(), any()))
                .thenReturn(0);

        GateTxnPayDebitConvergeRespDTO resp = service.convergeDebitStatusForSupplement(req());

        assertFalse(resp.getConverged(), "未真正收敛 MUST 返 converged=false");
        assertEquals(GateTxnPayRetCode.SUCCESS, resp.getRetCode(),
                "原单已是 SUCCESS MUST 当「重复支付」回成功码，让补款侧标待退款；NEVER 返 STATUS_REJECT");
        assertEquals("SUCCESS", resp.getDebitStatus(), "回传的 debitStatus MUST 是重读后的真实状态，不是旧快照");
        assertEquals("原订单已结清，本次未改动", resp.getRetMsg());
        verify(gateTxnPayNoticeService, never()).notifySettled(any(), anyString(), anyString());
    }

    @Test
    void stillNotConvergeableWhenRereadIsRetry() {
        when(gateTxnPayMapper.selectByOrderNo(ORIG_ORDER))
                .thenReturn(order("RETRY"))
                .thenReturn(order("RETRY"));
        when(gateTxnPayWriter.convergeDebitStatusForSupplement(anyString(), anyString(), any()))
                .thenReturn(0);

        GateTxnPayDebitConvergeRespDTO resp = service.convergeDebitStatusForSupplement(req());

        assertFalse(resp.getConverged());
        assertEquals(GateTxnPayRetCode.STATUS_REJECT, resp.getRetCode(),
                "重读仍是 RETRY 才允许判「状态不允许收敛」（真异常，需人工核对）");
        assertEquals("订单状态不允许收敛：RETRY", resp.getRetMsg());
    }

    @Test
    void convergedRowNotifiesOnce() {
        when(gateTxnPayMapper.selectByOrderNo(ORIG_ORDER)).thenReturn(order("RETRY"));
        when(gateTxnPayWriter.convergeDebitStatusForSupplement(anyString(), anyString(), any()))
                .thenReturn(1);

        GateTxnPayDebitConvergeRespDTO resp = service.convergeDebitStatusForSupplement(req());

        assertTrue(resp.getConverged());
        assertEquals("SUCCESS", resp.getDebitStatus());
        verify(gateTxnPayNoticeService).notifySettled(any(), eq("SUCCESS"), anyString());
    }

    private GateTxnPayDebitConvergeReqDTO req() {
        GateTxnPayDebitConvergeReqDTO request = new GateTxnPayDebitConvergeReqDTO();
        request.setOrigOrderNo(ORIG_ORDER);
        request.setTxnDate(TXN_DATE);
        request.setRemark("补款单号:SP20261120151345695542741");
        return request;
    }

    private GateTxnPay order(String debitStatus) {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo(ORIG_ORDER);
        order.setTxnDate(TXN_DATE);
        order.setDebitStatus(debitStatus);
        return order;
    }
}
