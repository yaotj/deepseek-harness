package com.chinasofti.huateng.facepay.api.device.tvm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.PaymentResult;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对外契约测试：报文字段名与响应 key 一律断言字面量。纯函数，不起 Spring。
 *
 * <p>这些用例的意义是<b>把「一字不改」钉死</b>——字段名或 retCode 被人「顺手改正」时，
 * 这里立刻红，而不是等设备联调才发现收不到值。</p>
 */
class TvmContractTest {

    @Test
    void genSjtOrderBizDataBindsByExactFieldNames() {
        String bizData = "{\"entryStationCode\":\"0101\",\"exitStationCode\":\"0205\","
                + "\"ticketPrice\":\"300\",\"singelTicketNum\":\"2\","
                + "\"singleTicketType\":\"0\",\"payType\":\"1\",\"deviceId\":\"TVM001\"}";

        RequestGenSjtOrderReqDTO dto = JSON.parseObject(bizData, RequestGenSjtOrderReqDTO.class);

        assertEquals("0101", dto.getEntryStationCode());
        assertEquals("0205", dto.getExitStationCode());
        assertEquals("300", dto.getTicketPrice());
        assertEquals("2", dto.getSingelTicketNum(), "字段名是 singelTicketNum（少一个 l），改名即收不到值");
        assertEquals("0", dto.getSingleTicketType());
        assertEquals("1", dto.getPayType());
        assertEquals("TVM001", dto.getDeviceId(), "deviceId 可能只出现在 bizData 里");
    }

    @Test
    void correctlySpelledFieldNameMustNotBind() {
        RequestGenSjtOrderReqDTO dto =
                JSON.parseObject("{\"singleTicketNum\":\"2\"}", RequestGenSjtOrderReqDTO.class);

        assertNull(dto.getSingelTicketNum(), "拼写正确的 singleTicketNum 不是契约字段，不该绑定上");
    }

    @Test
    void payResultBizDataBindsOrderNo() {
        RequestPayResultReqDTO dto = JSON.parseObject(
                "{\"orderNo\":\"F200202609081430050007\",\"userId\":\"u1\"}", RequestPayResultReqDTO.class);

        assertEquals("F200202609081430050007", dto.getOrderNo());
        assertEquals("u1", dto.getUserId());
    }

    @Test
    void genSjtOrderSuccessBodyHasExactlyFourKeys() {
        JSONObject body = TvmResponses.genSjtOrderSuccess("F200202609081430050007", "http://pay/x?y=1");

        assertEquals("0000", body.getString("retCode"));
        assertEquals("成功", body.getString("retMsg"));
        assertEquals("F200202609081430050007", body.getString("orderNo"));
        assertEquals("http://pay/x?y=1", body.getString("payUrl"));
        assertEquals(4, body.size());
    }

    @Test
    void payResultReturns2999WhenPaymentFailed() {
        JSONObject body = TvmResponses.payResult(PaymentResult.FAILED, "0C");

        assertEquals("2999", body.getString("retCode"),
                "失败单外层码 MUST 是 2999：两笔历史失败单重放实测旧应用回 2999，"
                        + "TVM 设备若按外层码判成败，恒回 0000 会把失败单读成成功");
        assertEquals("失败", body.getString("retMsg"));
        assertEquals("FAILED", body.getString("paymentResult"));
        assertEquals("失败", body.getString("paymentResultDesc"));
        assertEquals("0C", body.getString("paymentChannelCode"));
        assertEquals(5, body.size());
    }

    @Test
    void payResultOrderedIsUsedForUnknownToo() {
        JSONObject body = TvmResponses.payResult(PaymentResult.ORDERED, null);

        assertEquals("ORDERED", body.getString("paymentResult"));
        assertEquals("已下单", body.getString("paymentResultDesc"));
        assertTrue(body.containsKey("paymentChannelCode"), "渠道为空时仍要有该 key");
    }

    @Test
    void failBodyHasOnlyRetCodeAndMsg() {
        JSONObject body = TvmResponses.fail(DeviceRetCode.INVALID_PARAM, "orderNo不能为空");

        assertEquals("2002", body.getString("retCode"));
        assertEquals("orderNo不能为空", body.getString("retMsg"));
        assertEquals(2, body.size());
    }

    @Test
    void retCodeLiteralsMatchLegacyEnum() {
        assertEquals("0000", DeviceRetCode.SUCCESS.getCode());
        assertEquals("2999", DeviceRetCode.FAIL.getCode());
        assertEquals("2002", DeviceRetCode.INVALID_PARAM.getCode());
        assertEquals("2008", DeviceRetCode.ORDER_LOCKED.getCode());
        assertEquals(10, DeviceRetCode.values().length);
    }

    /**
     * 取票鉴权「无激活的订单」：码是 2003，但业务键 MUST 与成功响应一模一样、值为 null。
     *
     * <p>断言键序也一致——设备侧若按顺序解析，键序变化同样是契约变化。</p>
     */
    @Test
    void takeTicketAuthNoActiveOrderKeepsSuccessShapeWithNullValues() {
        JSONObject success = TvmResponses.takeTicketAuthSuccess("F20020260916", "TVM001",
                "0101", "0205", 300L, 2, "0", "0C");
        JSONObject body = TvmResponses.takeTicketAuthNoActiveOrder();

        assertEquals("2003", body.getString("retCode"), "码 MUST 保持 2003，NEVER 改成 2999");
        assertEquals("无激活的订单", body.getString("retMsg"));
        assertEquals(success.keySet(), body.keySet(), "键集与键序 MUST 与成功响应完全一致");
        assertEquals(10, body.size());
        for (String key : new String[] {"orderNo", "deviceId", "entryStationCode", "exitStationCode",
                "ticketPrice", "singelTicketNum", "singleTicketType", "paymentChannelCode"}) {
            assertTrue(body.containsKey(key), "缺键 " + key + "，设备侧取不到该字段");
            assertNull(body.get(key), key + " MUST 是 JSON null，NEVER 是字符串 \"null\" 或空串");
        }
    }

    /**
     * 上一个用例只证明了 Map 里有 null，本用例证明 <b>序列化后 null 键不会被吞掉</b>。
     *
     * <p>Controller 直接返回 fastjson2 {@code JSONObject}，而本工程没有引入 fastjson2 的
     * spring 扩展、也没有自定义 {@code HttpMessageConverter}，实际出站由 Spring Boot 默认的
     * Jackson 把它当 {@code Map} 写出。若哪天有人加上 {@code NON_NULL} 之类的全局配置，
     * 那 8 个键会**静默消失**、设备侧毫无察觉——这条断言就是那道防线。</p>
     */
    @Test
    void nullBusinessKeysSurviveJsonSerialization() throws Exception {
        String json = new ObjectMapper().writeValueAsString(TvmResponses.takeTicketAuthNoActiveOrder());

        assertTrue(json.contains("\"orderNo\":null"), "序列化后 orderNo 键被吞掉了: " + json);
        assertTrue(json.contains("\"singelTicketNum\":null"), "序列化后 singelTicketNum 键被吞掉了: " + json);
        assertTrue(json.contains("\"retCode\":\"2003\""), json);
    }

    /**
     * 「失败也要有全量字段」的推广结果：<b>每个带业务字段的接口，失败响应的键集与键序 MUST 等于成功响应</b>。
     *
     * <p>逐个断言 {@code keySet()} 相等而不是数个数 —— 键名写错、键序漂移都要红。
     * 一旦有人给某类响应加了新业务键但只改成功分支，这里立刻失败。</p>
     */
    @Test
    void everyFailShapeMatchesItsSuccessShape() {
        assertEquals(TvmResponses.genSjtOrderSuccess("F1", "http://pay").keySet(),
                TvmResponses.genSjtOrderFail(DeviceRetCode.INVALID_PARAM, "x").keySet(),
                "IF2A-01 拉码下单");
        assertEquals(TvmResponses.topupSuccess("T1", "http://pay").keySet(),
                TvmResponses.topupFail(DeviceRetCode.FAIL).keySet(), "IF2A-09 充值下单");
        assertEquals(TvmResponses.payResult(PaymentResult.SUCCESS, "0C").keySet(),
                TvmResponses.payResultFail(DeviceRetCode.INVALID_PARAM, "x").keySet(), "IF2A-03 查支付结果");
        assertEquals(TvmResponses.takeTicketAuthSuccess("F1", "TVM001", "0101", "0205", 300L, 2, "0", "0C")
                        .keySet(),
                TvmResponses.takeTicketAuthFail(DeviceRetCode.INVALID_PARAM, "x").keySet(), "IF2A-08 取票鉴权");

        assertEquals(BomResponses.successOrderNo("B1").keySet(),
                BomResponses.orderNoFail("8003", "x").keySet(), "IF8A-04 非现金下单");
        assertEquals(BomResponses.paymentResult(PaymentResult.SUCCESS, "支付成功").keySet(),
                BomResponses.paymentResultFail("8003", "x").keySet(), "IF8A-05/06 扫码支付与查结果");
        assertEquals(BomResponses.cardDataUpdate("AABB").keySet(),
                BomResponses.cardDataUpdateFail("8003", "x").keySet(), "IF5A-03 票卡更新");
        assertEquals(BomResponses.orderResult("SUCCESS", "支付成功", "B1", "20260916", 200L, "0C").keySet(),
                BomResponses.orderResultFail("8999", "x").keySet(), "单程票交易查询");
        assertEquals(BomResponses.cardDataAnalyse("1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                        java.util.List.of("A"), "11", "12").keySet(),
                BomResponses.cardDataAnalyseFail("8003", "x").keySet(), "IF5A-01 票卡分析");
        assertEquals(TvmResponses.refundSuccess("PROCESSING", "退款处理中", "F209...").keySet(),
                TvmResponses.refundFail("x").keySet(), "IF2A 设备退款（9999 分支）");
        assertEquals(TvmResponses.refundSuccess("SUCCESS", "退款成功", "F209...").keySet(),
                TvmResponses.refundOrderNotFound().keySet(), "IF2A 设备退款（2002 订单不存在分支）");
        assertEquals(BomResponses.refundSuccess("PROCESSING", "退款处理中", "F209...").keySet(),
                BomResponses.refundFail(BomResponses.CODE_FAIL, "x").keySet(), "BOM 单程票退款（8999 分支）");
        assertEquals(BomResponses.refundSuccess("SUCCESS", "退款成功", "F209...").keySet(),
                BomResponses.refundFail("9999", "订单号错误,没有找到匹配的订单").keySet(),
                "BOM 单程票退款（9999 订单不存在分支）");
    }

    /**
     * 失败响应的业务值 MUST 是 JSON null，<b>NEVER 填成 {@code FAILED} / {@code 0} / 空串</b>。
     *
     * <p>「查不到 / 参数缺失」与「这笔支付失败了」是两件事，后者仍走
     * {@code payResult(FAILED, ...)} 与 {@code paymentResult(FAILED, ...)}。
     * 若把前者也填成 FAILED，设备会对一笔可能已扣款的单子做错误处置。</p>
     */
    @Test
    void failShapesCarryNullBusinessValuesNotPlaceholders() {
        JSONObject tvmPayResult = TvmResponses.payResultFail(DeviceRetCode.INVALID_PARAM, "orderNo不能为空");
        assertEquals("2002", tvmPayResult.getString("retCode"));
        assertNull(tvmPayResult.get("paymentResult"), "NEVER 填 FAILED：语义是「查不到」不是「支付失败」");
        assertNull(tvmPayResult.get("paymentResultDesc"));
        assertNull(tvmPayResult.get("paymentChannelCode"));

        JSONObject bomOrderResult = BomResponses.orderResultFail("8999", "没有查找到出票信息");
        assertNull(bomOrderResult.get("transAmount"), "NEVER 填 \"0\"：给 0 会被读成「查到了、金额为零」");
        assertNull(bomOrderResult.get("orderNo"));

        JSONObject analyse = BomResponses.cardDataAnalyseFail("8004", "未注册用户");
        assertEquals("8004", analyse.getString("retCode"), "下游码 MUST 原样透传，NEVER 归一成 8999");
        assertNull(analyse.get("adviceOpt"), "NEVER 给空数组：那等于「分析成功、无建议操作」");
        assertTrue(analyse.containsKey("lastTransAmout") && analyse.containsKey("lastTikcetTransSeq"),
                "两个拼写错误是既有契约，失败分支也 MUST 照抄");

        JSONObject refund = TvmResponses.refundFail("订单状态不是支付成功,不能退款");
        assertEquals("9999", refund.getString("retCode"), "退款族的 9999 NEVER 归一成 2999");
        assertNull(refund.get("refundResult"),
                "NEVER 填 FAILED：语义是「请求没被受理」不是「退款做了但失败了」");
        assertNull(refund.get("refundResultDesc"));
        assertNull(refund.get("refundNo"), "没发起退款就没有退款单号，NEVER 给空串");

        JSONObject bomRefund = BomResponses.refundFail("9999", "订单号错误,没有找到匹配的订单");
        assertEquals("9999", bomRefund.getString("retCode"),
                "BOM 退款「订单不存在」那支是 BOM 域独一份的 9999，NEVER 归一成 8006 / 8999");
        assertNull(bomRefund.get("refundResult"),
                "NEVER 填 FAILED：语义是「请求没被受理」不是「退款做了但失败了」");
        assertNull(bomRefund.get("refundResultDesc"));
        assertNull(bomRefund.get("refundNo"), "没发起退款就没有退款单号，NEVER 给空串");
        assertEquals("8999", BomResponses.refundFail(BomResponses.CODE_FAIL, "该票已退款或状态不允许退款")
                .getString("retCode"), "BOM 族的失败码是 8999，NEVER 改成 TVM 的 2999");
    }

    /** 失败响应里那些 null 键同样 MUST 熬过 Jackson 序列化，理由见 {@link #nullBusinessKeysSurviveJsonSerialization}。 */
    @Test
    void failShapeNullKeysSurviveJsonSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertTrue(mapper.writeValueAsString(TvmResponses.genSjtOrderFail(DeviceRetCode.FAIL))
                .contains("\"payUrl\":null"));
        assertTrue(mapper.writeValueAsString(BomResponses.paymentResultFail("8003", "x"))
                .contains("\"paymentResult\":null"));
        assertTrue(mapper.writeValueAsString(BomResponses.cardDataUpdateFail("8003", "x"))
                .contains("\"cardData\":null"));
    }
}
