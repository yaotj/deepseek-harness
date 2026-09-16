package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.FacePayServer;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestGenSjtOrderReqDTO;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 过期订单收口验证。用本机 stub HTTP 服务扮演支付中心，因此这是**第一次覆盖到「支付中心正常应答」
 * 的成功路径**（真实凭据仍未拿到，报文内容由 stub 固定返回）。
 *
 * <p>同样会真的写测试库，行为与 {@link F2fTvmOrderServiceWriteTest} 一致：靠 {@code DB_HOST} 开关，
 * 用完按 orderNo 删除自己造的行。</p>
 */
@SpringBootTest(classes = FacePayServer.class)
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
class F2fOrderExpireReconcileTest {

    private static final AtomicReference<String> STUB_BODY = new AtomicReference<>("{}");

    private static HttpServer stub;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            byte[] body = STUB_BODY.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        stub.start();
        String base = "http://127.0.0.1:" + stub.getAddress().getPort() + "/pay";

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String privateKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        registry.add("pay.center.private-key", () -> privateKey);
        registry.add("pay.center.merchant-no", () -> "TEST_MERCHANT");
        registry.add("pay.center.jhm-key", () -> "test-jhm-key");
        registry.add("pay.center.checkout-counter-url", () -> "http://pay.test/api/v1/checkoutCounter");
        registry.add("pay.center.query-url", () -> base);
        registry.add("pay.center.pay-url", () -> base);
    }

    /** 只用来造单（{@code payType=0} 不碰支付中心）。 */
    @Autowired
    private F2fTvmOrderService service;

    /** 被测对象：收口三个方法 2026-09-16 从 {@code F2fTvmOrderService} 拆到这里（P2）。 */
    @Autowired
    private F2fOrderExpireService expireService;

    @Autowired
    private F2fOrderMapper orderMapper;

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
    void payCenterSaysSuccessThenOrderIsMarkedPaid() {
        stubStatus("SUCCESS");
        F2fOrder expired = anExpiredOrder();

        assertTrue(expireService.reconcileExpiredOrder(expired), "拿到明确结果即算收口");

        F2fOrder after = orderMapper.selectByOrderNo(expired.getOrderNo());
        assertEquals("PAID", after.getOrderStatus(), "过期收口发现已支付时必须补记为 PAID");
        assertNotNull(after.getPaidTms(), "PAID_TMS 必须写入");
    }

    @Test
    void payCenterSaysUnpaidThenOrderIsExpired() {
        stubStatus("UNPAID");
        F2fOrder expired = anExpiredOrder();

        assertTrue(expireService.reconcileExpiredOrder(expired));

        assertEquals("EXPIRED", orderMapper.selectByOrderNo(expired.getOrderNo()).getOrderStatus());
    }

    @Test
    void payCenterStillOrderedThenStatusUntouched() {
        stubStatus("ORDERED");
        F2fOrder expired = anExpiredOrder();

        assertFalse(expireService.reconcileExpiredOrder(expired), "状态不明时不算收口，下轮重试");

        assertEquals("CREATED", orderMapper.selectByOrderNo(expired.getOrderNo()).getOrderStatus(),
                "支付中心没给终态时 NEVER 动本地状态");
    }

    @Test
    void expiredCandidateIsPickedUpByScan() {
        stubStatus("ORDERED");
        F2fOrder expired = anExpiredOrder();

        List<String> scanned = expireService.loadExpiredCandidates(200).stream().map(F2fOrder::getOrderNo).toList();

        assertTrue(scanned.contains(expired.getOrderNo()), "已过期订单必须能被扫表捞到");
    }

    private void stubStatus(String status) {
        JSONObject data = new JSONObject();
        data.put("status", status);
        data.put("orderNo", "PC_STUB_0001");
        data.put("paymentVendor", "0C");
        JSONObject body = new JSONObject();
        body.put("code", "0");
        body.put("msg", "成功");
        body.put("data", data);
        STUB_BODY.set(body.toJSONString());
    }

    /** 造一笔已过期的 CREATED 订单：先正常下单（payType=0 不碰支付中心），再把过期时间改到过去。 */
    private F2fOrder anExpiredOrder() {
        RequestGenSjtOrderReqDTO dto = new RequestGenSjtOrderReqDTO();
        dto.setDeviceId("TVM_EXPIRE_TEST");
        dto.setEntryStationCode("0101");
        dto.setExitStationCode("0205");
        dto.setTicketPrice("300");
        dto.setSingelTicketNum("1");
        dto.setSingleTicketType("0");
        dto.setPayType("0");
        String orderNo = service.createSingleTicketOrder(dto).getString("orderNo");
        created.add(orderNo);
        new JdbcTemplate(dataSource).update(
                "UPDATE F2F_ORDER SET EXPIRE_TMS = SYSTIMESTAMP - INTERVAL '10' MINUTE WHERE ORDER_NO = ?", orderNo);
        return orderMapper.selectByOrderNo(orderNo);
    }
}
