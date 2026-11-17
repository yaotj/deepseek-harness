package com.chinasofti.huateng.gatetxnpay.paysign;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.model.app.GatePayRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class GatePayRequestFactoryTest {

    private final GatePayRequestFactory factory =
            new GatePayRequestFactory("withholding", "1", "地铁乘车扣费", "地铁乘车费用", 60L);

    private GateTxnPay orderWithIndustryDetail(String industryDetail) {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo("GT20261117130759442000384");
        order.setTxnDate("20261117");
        order.setCardId("0426090949000384");
        order.setCardType("0441");
        order.setThirdUserId("00523017");
        order.setTrxType("02");
        order.setTicketTransSeq("14");
        order.setTotalAmount(600);
        order.setIndustryDetail(industryDetail);
        return order;
    }

    @Test
    @DisplayName("快照存在：V1.2 快照原样采用 + payTriggerType 注入，NEVER 重算快照键")
    void snapshotIsUsedWithTriggerInjected() {
        String snapshot = "{\"orderDate\":\"2026-11-17 13:07:59\",\"cardNum\":\"0426090949000384\","
                + "\"phone\":\"13800138000\",\"ticketFlag\":\"N\",\"cumulativeType\":\"01\","
                + "\"ticketTransSeq\":\"14\",\"tikcetTransSeq\":\"14\"}";

        GatePayRequestDTO request = factory.build(orderWithIndustryDetail(snapshot),
                null, AlipayTripPayRequestFactory.PAY_TRIGGER_FIRST);

        JSONObject detail = JSON.parseObject(request.getIndustryDetail());
        assertEquals("01", detail.getString("payTriggerType"), "首次支付 MUST 标 01");
        assertEquals("2026-11-17 13:07:59", detail.getString("orderDate"), "快照键 MUST 原样透传");
        assertEquals("13800138000", detail.getString("phone"), "V1.2 必填键 phone 随快照出网");
        assertEquals("N", detail.getString("ticketFlag"));
        assertEquals("01", detail.getString("cumulativeType"));
        assertEquals("14", detail.getString("tikcetTransSeq"), "双键同值随快照出网");
        assertFalse(detail.containsKey("cardId"), "NEVER 再把订单实体键混进行业明细");
    }

    @Test
    @DisplayName("补偿重推：payTriggerType=02 覆盖快照既有值（同一快照第二次发起）")
    void compensationTriggerOverrides() {
        String snapshot = "{\"orderDate\":\"2026-11-17 13:07:59\",\"payTriggerType\":\"01\"}";

        GatePayRequestDTO request = factory.build(orderWithIndustryDetail(snapshot),
                null, AlipayTripPayRequestFactory.PAY_TRIGGER_COMPENSATION);

        JSONObject detail = JSON.parseObject(request.getIndustryDetail());
        assertEquals("02", detail.getString("payTriggerType"), "重推 MUST 把快照里的 01 盖成 02");
    }

    @Test
    @DisplayName("快照缺失（历史行/组装失败）：回落订单实体 JSON + payTriggerType 照常注入")
    void missingSnapshotFallsBackToEntityDump() {
        GatePayRequestDTO request = factory.build(orderWithIndustryDetail(null),
                null, AlipayTripPayRequestFactory.PAY_TRIGGER_FIRST);

        JSONObject detail = JSON.parseObject(request.getIndustryDetail());
        assertEquals("01", detail.getString("payTriggerType"), "fallback 也 MUST 注入触发方式");
        assertEquals("GT20261117130759442000384", detail.getString("orderNo"), "实体兜底键保留（排查交易来源能力不丢）");
        assertEquals("0426090949000384", detail.getString("cardId"));
    }

    @Test
    @DisplayName("快照非法 JSON：原样透传 + MUST NOT 抛异常（资损方向：缺字段好过扣款不发）")
    void illegalSnapshotPassedThrough() {
        GatePayRequestDTO request = factory.build(orderWithIndustryDetail("not-a-json"),
                null, AlipayTripPayRequestFactory.PAY_TRIGGER_COMPENSATION);

        assertEquals("not-a-json", request.getIndustryDetail());
    }

    @Test
    @DisplayName("快照键序在注入后保持（fastjson2 保序）")
    void keyOrderPreservedAfterInjection() {
        String snapshot = "{\"orderDate\":\"2026-11-17 13:07:59\",\"cardNum\":\"0426090949000384\"}";

        GatePayRequestDTO request = factory.build(orderWithIndustryDetail(snapshot),
                null, AlipayTripPayRequestFactory.PAY_TRIGGER_FIRST);

        String detail = request.getIndustryDetail();
        assertTrue(detail.indexOf("\"orderDate\"") < detail.indexOf("\"payTriggerType\""), "注入键追加在快照键之后");
        assertEquals(snapshot.indexOf("\"cardNum\""), detail.indexOf("\"cardNum\""));
    }
}
