package com.chinasofti.huateng.gatetxnpay.paysign;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 支付宝出行免密扣费报文组装特征测试，聚焦 {@code payTriggerType} 注入
 * （V1.2 文档必填：01-首次支付，02-补偿支付）。
 */
class AlipayTripPayRequestFactoryTest {

    private final AlipayTripPayRequestFactory factory =
            new AlipayTripPayRequestFactory("TRIP", "05", "1", "地铁乘车扣费", "地铁乘车费用", 60);

    private GateTxnPay orderWithIndustryDetail(String industryDetail) {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo("ORD20261117001");
        order.setCardId("0426091000000013");
        order.setThirdUserId("2088ALIPAYUSER");
        order.setTicketTransSeq("T20261117000001");
        order.setTotalAmount(700);
        order.setIndustryDetail(industryDetail);
        return order;
    }

    @Test
    @DisplayName("首次支付：payTriggerType=01 注入 industryDetail，快照其余键原样保留")
    void firstPayTriggerInjected() {
        String snapshot = "{\"orderDate\":\"2026-11-17 09:30:00\",\"cardNum\":\"0426091000000013\",\"cumulativeType\":\"01\"}";

        AlipayTripRequestPayReqDTO request = factory.build(orderWithIndustryDetail(snapshot),
                AlipayTripPayRequestFactory.PAY_TRIGGER_FIRST);

        JSONObject detail = JSON.parseObject(request.getIndustryDetail());
        assertEquals("01", detail.getString("payTriggerType"));
        assertEquals("2026-11-17 09:30:00", detail.getString("orderDate"), "快照其余键 MUST NOT 被改写");
        assertEquals("01", detail.getString("cumulativeType"));
    }

    @Test
    @DisplayName("补偿支付：payTriggerType=02 覆盖既有值（同一快照第二次发起）")
    void compensationTriggerOverrides() {
        String snapshot = "{\"orderDate\":\"2026-11-17 09:30:00\",\"payTriggerType\":\"01\"}";

        AlipayTripRequestPayReqDTO request = factory.build(orderWithIndustryDetail(snapshot),
                AlipayTripPayRequestFactory.PAY_TRIGGER_COMPENSATION);

        JSONObject detail = JSON.parseObject(request.getIndustryDetail());
        assertEquals("02", detail.getString("payTriggerType"), "重推 MUST 把快照里的 01 盖成 02");
    }

    @Test
    @DisplayName("快照为 null：industryDetail 原样为 null，扣款 MUST NOT 被字段注入阻断")
    void nullSnapshotTolerated() {
        AlipayTripRequestPayReqDTO request = factory.build(orderWithIndustryDetail(null),
                AlipayTripPayRequestFactory.PAY_TRIGGER_FIRST);
        assertNull(request.getIndustryDetail());
    }

    @Test
    @DisplayName("快照非法 JSON：原样透传 + MUST NOT 抛异常")
    void illegalSnapshotPassedThrough() {
        AlipayTripRequestPayReqDTO request = factory.build(orderWithIndustryDetail("not-a-json"),
                AlipayTripPayRequestFactory.PAY_TRIGGER_COMPENSATION);
        assertEquals("not-a-json", request.getIndustryDetail(), "资损方向：缺字段好过扣款不发");
    }

    @Test
    @DisplayName("出站组装的键序在注入后保持（fastjson2 保序，对端按序解析无副作用）")
    void keyOrderPreservedAfterInjection() {
        String snapshot = "{\"orderDate\":\"2026-11-17 09:30:00\",\"cardNum\":\"0426091000000013\",\"exitStationCode\":\"0230\"}";

        AlipayTripRequestPayReqDTO request = factory.build(orderWithIndustryDetail(snapshot),
                AlipayTripPayRequestFactory.PAY_TRIGGER_FIRST);

        String detail = request.getIndustryDetail();
        assertEquals(snapshot.indexOf("\"orderDate\""), detail.indexOf("\"orderDate\""));
        assertEquals(snapshot.indexOf("\"exitStationCode\""), detail.indexOf("\"exitStationCode\""));
        assertEquals("0426091000000013", JSON.parseObject(detail).getString("cardNum"));
    }
}
