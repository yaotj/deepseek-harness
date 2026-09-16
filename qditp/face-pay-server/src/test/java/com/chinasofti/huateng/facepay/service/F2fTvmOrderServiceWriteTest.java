package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.FacePayServer;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestGenSjtOrderReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.facepay.api.paycenter.PayNoticeReqDTO;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 下单写路径的落库验证。<b>会真的往测试库插数据</b>，因此：
 * <ul>
 *   <li>靠 {@code DB_HOST} 环境变量开关，无库环境整类跳过；</li>
 *   <li>每个用例结束后在 {@link #cleanup()} 里按 orderNo 删除自己造的行，先删
 *       {@code F2F_PAYMENT} 再删 {@code F2F_ORDER}；</li>
 *   <li>只 INSERT 新行、不改任何既有数据，还原 SQL 即
 *       {@code DELETE FROM F2F_PAYMENT/F2F_ORDER WHERE ORDER_NO = ?}。</li>
 * </ul>
 *
 * <p>支付中心地址故意指向 {@code 127.0.0.1:1}（必然连不上），用来验证<b>「对端没答上来时订单不得
 * 被写成失败」</b>这条最关键的行为。成功路径需要真实支付中心凭据，暂无法覆盖。</p>
 */
@SpringBootTest(classes = FacePayServer.class)
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
class F2fTvmOrderServiceWriteTest {

    private static final String UNREACHABLE = "http://127.0.0.1:1/never";

    @DynamicPropertySource
    static void payCenterProps(DynamicPropertyRegistry registry) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String privateKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        registry.add("pay.center.private-key", () -> privateKey);
        registry.add("pay.center.merchant-no", () -> "TEST_MERCHANT");
        registry.add("pay.center.jhm-key", () -> "test-jhm-key");
        registry.add("pay.center.checkout-counter-url", () -> "http://pay.test/api/v1/checkoutCounter");
        registry.add("pay.center.pay-url", () -> UNREACHABLE);
        registry.add("pay.center.query-url", () -> UNREACHABLE);
        registry.add("pay.center.connect-timeout-ms", () -> "500");
        registry.add("pay.center.read-timeout-ms", () -> "500");
    }

    @Autowired
    private F2fTvmOrderService service;

    /**
     * 支付结果侧三条（{@code queryPayResult} / {@code receivePayNotice}）的宿主。
     * 2026-09-16 从 {@code F2fTvmOrderService} 拆出（P2），断言与报文一行未改。
     */
    @Autowired
    private F2fTvmPayResultService payResultService;

    @Autowired
    private F2fOrderMapper orderMapper;

    @Autowired
    private F2fPaymentMapper paymentMapper;

    @Autowired
    private DataSource dataSource;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void cleanup() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String orderNo : created) {
            jdbc.update("DELETE FROM F2F_PAYMENT WHERE ORDER_NO = ?", orderNo);
            jdbc.update("DELETE FROM F2F_ORDER WHERE ORDER_NO = ?", orderNo);
        }
        created.clear();
    }

    @Test
    void payTypeZeroPersistsOrderAndInitPaymentWithoutCallingPayCenter() {
        JSONObject response = service.createSingleTicketOrder(request("0"));

        assertEquals("0000", response.getString("retCode"));
        String orderNo = response.getString("orderNo");
        created.add(orderNo);
        assertTrue(response.getString("payUrl").contains("orderNo=" + orderNo));

        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        assertNotNull(order, "订单必须已落库");
        assertEquals("CREATED", order.getOrderStatus());
        assertEquals("02", order.getChannel());
        assertEquals("01", order.getBizType());
        assertEquals(600L, order.getOrderAmount(), "300 分 × 2 张 = 600 分");
        assertEquals(2, order.getTicketNum());
        assertNotNull(order.getExpireTms(), "过期时间必须写入，方向1 的兜底依赖它");

        List<F2fPayment> payments = paymentMapper.selectByOrderNo(orderNo);
        assertEquals(1, payments.size());
        assertEquals("INIT", payments.get(0).getPayStatus());
        assertEquals("0", payments.get(0).getPayType());
        assertNotNull(payments.get(0).getPayUrl());
    }

    @Test
    void payCenterUnreachableKeepsOrderCreatedAndMarksPaymentUnknown() {
        JSONObject response = service.createSingleTicketOrder(request("1"));

        assertEquals("2999", response.getString("retCode"), "拿不到二维码串，对设备只能回失败");

        F2fOrder order = latestCreatedOrder();
        assertEquals("CREATED", order.getOrderStatus(),
                "对端没答上来时 NEVER 写 PAY_FAILED，必须留在 CREATED 等收口");

        List<F2fPayment> payments = paymentMapper.selectByOrderNo(order.getOrderNo());
        assertEquals(1, payments.size());
        assertEquals("UNKNOWN", payments.get(0).getPayStatus(), "传输失败落 UNKNOWN，不是 FAILED");
        assertNotNull(payments.get(0).getRetMsg(), "失败原因要留证");
        assertNotNull(payments.get(0).getRequestBody(), "请求报文要留证");
    }

    @Test
    void queryReturnsOrderedWhenPayCenterUnreachable() {
        JSONObject created0 = service.createSingleTicketOrder(request("0"));
        String orderNo = created0.getString("orderNo");
        created.add(orderNo);

        RequestPayResultReqDTO query = new RequestPayResultReqDTO();
        query.setOrderNo(orderNo);
        JSONObject response = payResultService.queryPayResult(query);

        assertEquals("0000", response.getString("retCode"), "查询接口 retCode 恒为 0000");
        assertEquals("ORDERED", response.getString("paymentResult"), "查不到结果时让设备继续轮询");
        assertEquals("CREATED", orderMapper.selectByOrderNo(orderNo).getOrderStatus(), "状态不得被改动");
    }

    @Test
    void payNoticeSuccessMarksOrderPaidAndBackfillsEvidence() {
        String orderNo = service.createSingleTicketOrder(request("0")).getString("orderNo");
        created.add(orderNo);

        JSONObject ack = payResultService.receivePayNotice(notice(orderNo, "SUCCESS"));

        assertEquals("0", ack.getString("code"), "已受理必须回 code=0，否则支付中心会重推");
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        assertEquals("PAID", order.getOrderStatus());
        assertNotNull(order.getPaidTms());
        F2fPayment payment = paymentMapper.selectByOrderNo(orderNo).get(0);
        assertEquals("SUCCESS", payment.getPayStatus());
        assertEquals("PC_CB_0001", payment.getPayCenterOrderNo(), "支付中心订单号要回填");
        assertEquals("CH_CB_0001", payment.getChannelOrderNo(), "渠道订单号要回填");
        assertEquals("0C", payment.getPayChannelCode(), "渠道码要回填，否则查询接口回吐的 paymentChannelCode 恒为 null");

        RequestPayResultReqDTO query = new RequestPayResultReqDTO();
        query.setOrderNo(orderNo);
        JSONObject queried = payResultService.queryPayResult(query);
        assertEquals("0C", queried.getString("paymentChannelCode"), "查询接口必须回吐回调带来的渠道码");
    }

    @Test
    void duplicateSuccessNoticeIsIdempotent() {
        String orderNo = service.createSingleTicketOrder(request("0")).getString("orderNo");
        created.add(orderNo);

        assertEquals("0", payResultService.receivePayNotice(notice(orderNo, "SUCCESS")).getString("code"));
        JSONObject second = payResultService.receivePayNotice(notice(orderNo, "SUCCESS"));

        assertEquals("0", second.getString("code"), "重复回调直接回成功");
        assertEquals("PAID", orderMapper.selectByOrderNo(orderNo).getOrderStatus());
    }

    @Test
    void payNoticeWithUnknownStatusReturnsFailSoPayCenterRetries() {
        String orderNo = service.createSingleTicketOrder(request("0")).getString("orderNo");
        created.add(orderNo);

        JSONObject ack = payResultService.receivePayNotice(notice(orderNo, "WHATEVER"));

        assertEquals("-1", ack.getString("code"), "状态不明必须回失败让对端重推，NEVER 回成功");
        assertEquals("CREATED", orderMapper.selectByOrderNo(orderNo).getOrderStatus(), "状态不得被改动");
    }

    @Test
    void payNoticeForMissingOrderReturnsOrderNotExist() {
        JSONObject ack = payResultService.receivePayNotice(notice("F2000000000000000000", "SUCCESS"));

        assertEquals("2001", ack.getString("code"));
    }

    @Test
    void bomSaleOrderPersistsWithBomChannelAndNoPaymentRow() {
        JSONObject response = service.createBomSaleOrder(bomRequest());

        assertEquals("0000", response.getString("retCode"));
        String orderNo = response.getString("orderNo");
        created.add(orderNo);
        assertNull(response.getString("payUrl"), "BOM 售票没有二维码，NEVER 回 payUrl");

        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        assertNotNull(order, "订单必须已落库");
        assertEquals("03", order.getChannel(), "渠道必须记成 BOM，旧实现三张表里一个 03 都没存");
        assertEquals("01", order.getBizType(), "BOM 卖的是单程票，业务类型仍是购票");
        assertEquals("CREATED", order.getOrderStatus());
        assertEquals(600L, order.getOrderAmount());
        assertEquals("BOM_WRITE_TEST", order.getDeviceId());
        assertNotNull(order.getExpireTms(), "BOM 单也必须有失效时间，否则弃单永远停在 CREATED");

        assertTrue(paymentMapper.selectByOrderNo(orderNo).isEmpty(),
                "下单阶段不碰支付中心，NEVER 预先插支付尝试行");
    }

    @Test
    void bomSaleOrderRejectsNonPositiveTicketNum() {
        RequestGenSjtOrderReqDTO bad = bomRequest();
        bad.setSingelTicketNum("0");

        JSONObject response = service.createBomSaleOrder(bad);

        assertEquals("2002", response.getString("retCode"), "校验失败回 TVM 族，与同 URL 的 TVM 分支一致");
        assertNull(response.getString("orderNo"), "被拒时不得取号落库");
    }

    private PayNoticeReqDTO notice(String merchantOrderNo, String status) {
        PayNoticeReqDTO dto = new PayNoticeReqDTO();
        dto.setMerchantOrderNo(merchantOrderNo);
        dto.setOrderNo("PC_CB_0001");
        dto.setChannelOrderNo("CH_CB_0001");
        dto.setStatus(status);
        dto.setPaymentVendor("0C");
        dto.setTotalAmount("600");
        return dto;
    }

    /** 第二个用例拿不到返回体里的 orderNo，用「本设备最近一笔」定位并登记待清理。 */
    private F2fOrder latestCreatedOrder() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String orderNo = jdbc.queryForObject(
                "SELECT ORDER_NO FROM (SELECT ORDER_NO FROM F2F_ORDER WHERE DEVICE_ID = ?"
                        + " ORDER BY CREATE_TMS DESC) WHERE ROWNUM = 1", String.class, "TVM_WRITE_TEST");
        created.add(orderNo);
        return orderMapper.selectByOrderNo(orderNo);
    }

    private RequestGenSjtOrderReqDTO request(String payType) {
        RequestGenSjtOrderReqDTO dto = new RequestGenSjtOrderReqDTO();
        dto.setDeviceId("TVM_WRITE_TEST");
        dto.setEntryStationCode("0101");
        dto.setExitStationCode("0205");
        dto.setTicketPrice("300");
        dto.setSingelTicketNum("2");
        dto.setSingleTicketType("0");
        dto.setPayType(payType);
        return dto;
    }

    /** BOM 柜台售票，{@code payType} 与 TVM 同为 0，但下单阶段完全不看它。 */
    private RequestGenSjtOrderReqDTO bomRequest() {
        RequestGenSjtOrderReqDTO dto = request("0");
        dto.setDeviceId("BOM_WRITE_TEST");
        dto.setProviderId("03");
        return dto;
    }
}
