package com.chinasofti.huateng.transquery.query;

import com.chinasofti.huateng.model.app.TransRecordDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link TransRecordAssembler} 单测。
 *
 * <p>2026-09-14 随「删除中间模型 TransListEntry」一并补齐：这六个用例覆盖的都是
 * <b>改坏了不报错、只在 APP 上看出来</b>的口径 —— 金额单位（分，NEVER 转元）、
 * 扣款结果值域（只有 SUCCESS 是 "0"）、{@code pay == null} 分支
 * （BOM 补站单 / 日票免扣费单没有 PAY_TXN_DETAIL 行）、pay 侧字段取值源、
 * 站名缺失回落站点编码，以及 {@code gate == null} 直接返 null。<b>NEVER 删</b>。</p>
 */
class TransRecordAssemblerTest {

    @Test
    void 金额一律按分输出且原价取originalFare() {
        GateTxnPayListDTO gate = gate();
        gate.setTotalAmount(200);
        gate.setOriginalFare(300);

        TransRecordDTO dto = TransRecordAssembler.assemble(gate, pay());

        // 库内 200 分即 2 元；2026-09-07 曾在此转元，APP 再除 100 显示成 0.02
        assertEquals("200", dto.getPayAmount());
        assertEquals(Integer.valueOf(300), dto.getOriginalFare());
        // totalAmount 是 APP 侧的「原价」字段，与 originalFare 同源，不是实付
        assertEquals(Integer.valueOf(300), dto.getTotalAmount());
    }

    @Test
    void 扣款结果以闸机侧为准仅SUCCESS映射为0() {
        assertEquals("0", TransRecordAssembler.toAppDebitResult("SUCCESS", null));
        assertEquals("1", TransRecordAssembler.toAppDebitResult("FAIL", null));
        assertEquals("1", TransRecordAssembler.toAppDebitResult("PROCESSING", null));
        // 闸机侧是权威列：即便支付明细侧是 FAIL，也按 SUCCESS 算（2026-09-10 生产缺陷）
        assertEquals("0", TransRecordAssembler.toAppDebitResult("SUCCESS", "FAIL"));
        // 仅当闸机侧为空 / 空串才回落到支付明细侧
        assertEquals("0", TransRecordAssembler.toAppDebitResult(null, "SUCCESS"));
        assertEquals("0", TransRecordAssembler.toAppDebitResult("  ", "SUCCESS"));
        // 未知值 NEVER 当成功
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
        // 闸机侧 SUCCESS 仍要显示已支付，NEVER 因为没有支付明细行就退化成 "1"
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
