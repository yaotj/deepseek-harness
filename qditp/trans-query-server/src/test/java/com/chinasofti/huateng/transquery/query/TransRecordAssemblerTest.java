package com.chinasofti.huateng.transquery.query;

import com.chinasofti.huateng.model.app.TransRecordDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** {@link TransRecordAssembler} 单测。 */
class TransRecordAssemblerTest {

    @Test
    void 金额一律按分输出且原价取originalFare() {
        GateTxnPayListDTO gate = gate();
        gate.setTotalAmount(200);
        gate.setOriginalFare(300);

        TransRecordDTO dto = TransRecordAssembler.assemble(gate, pay());

        assertEquals("200", dto.getPayAmount());
        assertEquals(Integer.valueOf(300), dto.getOriginalFare());
        assertEquals(Integer.valueOf(300), dto.getTotalAmount());
    }

    @Test
    void 扣款结果以闸机侧为准仅SUCCESS映射为0() {
        assertEquals("0", TransRecordAssembler.toAppDebitResult("SUCCESS", null));
        assertEquals("1", TransRecordAssembler.toAppDebitResult("FAIL", null));
        assertEquals("1", TransRecordAssembler.toAppDebitResult("PROCESSING", null));
        assertEquals("0", TransRecordAssembler.toAppDebitResult("SUCCESS", "FAIL"));
        assertEquals("0", TransRecordAssembler.toAppDebitResult(null, "SUCCESS"));
        assertEquals("0", TransRecordAssembler.toAppDebitResult("  ", "SUCCESS"));
        assertEquals("1", TransRecordAssembler.toAppDebitResult(null, null));
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
        assertEquals(Integer.valueOf(2), dto.getDiscountFee());
        assertEquals("满减", dto.getDiscountInfo());
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
