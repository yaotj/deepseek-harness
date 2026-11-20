package com.chinasofti.huateng.transquery.query;

import com.chinasofti.huateng.model.app.TransRecordDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransRecordAssemblerTest {

    @Test
    void 有回调金额时payAmount取实付现金且totalAmount取订单总额() {
        GateTxnPayListDTO gate = gate();
        gate.setTotalAmount(200);
        gate.setOriginalFare(300);
        PayTxnDetailDTO pay = pay();
        pay.setCashAmount(180);
        pay.setTotalAmount(200);

        TransRecordDTO dto = TransRecordAssembler.assemble(gate, pay);

        assertEquals("180", dto.getPayAmount());
        assertEquals(Integer.valueOf(300), dto.getOriginalFare());
        assertEquals("200", dto.getTotalAmount());
    }

    @Test
    void 支付明细缺回调金额时payAmount回落应扣总额且totalAmount回落原始票价() {
        GateTxnPayListDTO gate = gate();
        gate.setTotalAmount(200);
        gate.setOriginalFare(300);

        TransRecordDTO dto = TransRecordAssembler.assemble(gate, pay());

        assertEquals("200", dto.getPayAmount());
        assertEquals(Integer.valueOf(300), dto.getOriginalFare());
        assertEquals("300", dto.getTotalAmount());
    }

    @Test
    void 扣款结果以闸机侧为准成功映射0在途映射空其余映射2() {
        assertEquals("0", TransRecordAssembler.toAppDebitResult("SUCCESS", null));
        assertEquals("2", TransRecordAssembler.toAppDebitResult("FAIL", null));
        assertNull(TransRecordAssembler.toAppDebitResult("PROCESSING", null),
                "IT-082 阶段三：在途 MUST 返 null（APP 按「空 = 支付中」渲染）");
        assertNull(TransRecordAssembler.toAppDebitResult("RETRY", null),
                "补偿重试中的单同样没有定论，MUST 按支付中");
        assertNull(TransRecordAssembler.toAppDebitResult("INIT", null),
                "已落单未发起扣款 MUST 按支付中");
        assertEquals("2", TransRecordAssembler.toAppDebitResult("CLOSED", null),
                "关单是终态失败，NEVER 当支付中，否则 APP 永远转圈");
        assertEquals("2", TransRecordAssembler.toAppDebitResult("UNKNOWN", null),
                "未知值 MUST fail-closed 落 2，NEVER 按支付中兜底");
        assertEquals("0", TransRecordAssembler.toAppDebitResult("SUCCESS", "FAIL"));
        assertEquals("0", TransRecordAssembler.toAppDebitResult(null, "SUCCESS"));
        assertEquals("0", TransRecordAssembler.toAppDebitResult("  ", "SUCCESS"));
        assertNull(TransRecordAssembler.toAppDebitResult(null, "PROCESSING"));
        assertEquals("2", TransRecordAssembler.toAppDebitResult(null, null));
    }

    @Test
    void IT082对外扣款结果只产出0空2且永不产出1和3() {
        // 旧 ITP 的 1=线下补款人工成功（成功态）、空=支付中；新仓暂无补款数据来源，1 不产出。
        // APP 侧口径：0 成功、1 线下补款（成功）、空 支付中、其他一律失败。
        String[] authoritativeStatuses = {"SUCCESS", "FAIL", "PROCESSING", "RETRY", "INIT", "CLOSED",
                null, "", "  ", "UNKNOWN"};
        for (String status : authoritativeStatuses) {
            String result = TransRecordAssembler.toAppDebitResult(status, null);
            assertTrue(result == null || "0".equals(result) || "2".equals(result),
                    () -> "status=" + status + " 时只允许 0 / 空 / 2，实得 " + result);
        }
    }

    @Test
    void 闸机侧在途时整条记录不带扣款结果() {
        GateTxnPayListDTO gate = gate();
        gate.setDebitStatus("PROCESSING");

        TransRecordDTO dto = TransRecordAssembler.assemble(gate, pay());

        assertNull(dto.getDebitRequestResult(),
                "闸机侧在途 MUST 覆盖支付明细的 SUCCESS，按「支付中」输出");
    }

    @Test
    void 无支付明细行时pay侧字段留空而闸机侧照常输出() {
        GateTxnPayListDTO gate = gate();
        gate.setDebitStatus("SUCCESS");

        TransRecordDTO dto = TransRecordAssembler.assemble(gate, null);

        assertEquals("", dto.getPayChannelCode());
        assertEquals("", dto.getPayOrderNoDate());
        assertEquals("", dto.getPayTradeOrderNo());
        assertNull(dto.getDiscountFee());
        assertNull(dto.getDiscountInfo());
        assertEquals("0", dto.getDebitRequestResult());
        assertEquals("GT20260914000000000000001", dto.getTradeOrderNo());
    }

    @Test
    void 支付明细字段取自PAY_TXN_DETAIL() {
        TransRecordDTO dto = TransRecordAssembler.assemble(gate(), pay());

        assertEquals("ALIPAY", dto.getPayChannelCode());
        assertEquals("20260914153638", dto.getPayOrderNoDate());
        assertEquals("CH20260914001", dto.getPayTradeOrderNo());
        assertEquals("2", dto.getDiscountFee());
        assertEquals("满减", dto.getDiscountInfo());
    }

    @Test
    void 优惠金额自算优先于渠道couponAmount() {
        PayTxnDetailDTO pay = pay();
        pay.setCouponAmount(1800);

        TransRecordDTO dto = TransRecordAssembler.assemble(gate(), pay);

        assertEquals("2", dto.getDiscountFee());
    }

    @Test
    void 优惠金额自算缺失时回落渠道couponAmount() {
        PayTxnDetailDTO pay = pay();
        pay.setDiscountFee(null);
        pay.setCouponAmount(1800);

        TransRecordDTO dto = TransRecordAssembler.assemble(gate(), pay);

        assertEquals("1800", dto.getDiscountFee());
    }

    @Test
    void 优惠明细自算优先于渠道明细() {
        PayTxnDetailDTO pay = pay();
        pay.setChannelDiscountInfo("[{\"amount\":1800,\"type\":\"COUPON\"}]");

        TransRecordDTO dto = TransRecordAssembler.assemble(gate(), pay);

        assertEquals("满减", dto.getDiscountInfo());
    }

    @Test
    void 优惠明细自算缺失时回落渠道明细原文() {
        PayTxnDetailDTO pay = pay();
        pay.setDiscountInfo(null);
        pay.setChannelDiscountInfo("[{\"amount\":1800,\"name\":\"聚合优惠-活动储值券-定额20\",\"type\":\"COUPON\"}]");

        TransRecordDTO dto = TransRecordAssembler.assemble(gate(), pay);

        assertEquals("[{\"amount\":1800,\"name\":\"聚合优惠-活动储值券-定额20\",\"type\":\"COUPON\"}]",
                dto.getDiscountInfo());
    }

    @Test
    void 站名缺失时回落到站点编码() {
        GateTxnPayListDTO gate = gate();
        gate.setEntryStationName(null);
        gate.setExitStationName("五四广场");

        TransRecordDTO dto = TransRecordAssembler.assemble(gate, null);

        assertEquals("0101", dto.getEntryStationName());
        assertEquals("五四广场", dto.getExitStationName());
    }

    @Test
    void 闸机行为空整条记录无意义() {
        assertNull(TransRecordAssembler.assemble(null, pay()));
    }

    private static GateTxnPayListDTO gate() {
        GateTxnPayListDTO gate = new GateTxnPayListDTO();
        gate.setOrderNo("GT20260914000000000000001");
        gate.setCardId("0178885088135717");
        gate.setDebitStatus("FAIL");
        gate.setTotalAmount(200);
        gate.setOriginalFare(200);
        gate.setInStation("0101");
        gate.setOutStation("0102");
        gate.setEntryStationName("青岛站");
        gate.setExitStationName("市政府");
        gate.setInTime("20260914153000");
        gate.setOutTime("20260914153600");
        return gate;
    }

    private static PayTxnDetailDTO pay() {
        PayTxnDetailDTO pay = new PayTxnDetailDTO();
        pay.setOrderNo("GT20260914000000000000001");
        pay.setPaymentVendor("ALIPAY");
        pay.setPayTime("20260914153638");
        pay.setChannelOrderNo("CH20260914001");
        pay.setDiscountFee(2);
        pay.setDiscountInfo("满减");
        pay.setDebitRequestResult("SUCCESS");
        return pay;
    }
}
