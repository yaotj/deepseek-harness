package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BOM 补站单（{@code adviceOpt} 005 / 006 / 020）MUST 落单、MUST NOT 扣款：钱是 BOM 现场收的。
 *
 * <p>钉住两件事：①`adviceOpt` 从报文搬进订单并绑进 insert；②`isBomSupplement` 认这三个码 ——
 * 少了它，006 的 `trxAmount > 0` 会走真实免密扣款分支，乘客重复付费。
 */
class GateBomSupplementPersistenceTest {

    @ParameterizedTest
    @ValueSource(strings = {"005", "006", "020"})
    void adviceOptReachesOrderAndSkipsDebit(String adviceOpt) throws Exception {
        var request = new GateTxnPayReqDTO();
        request.setCardId("0426090949000159");
        request.setCardType("0441");
        request.setHandleDateTime("20260922153642");
        request.setTrxAmount("200");
        request.setAdviceOpt(adviceOpt);
        var service = new GateTxnPayServiceImpl(null, null, null, null, null, null, null, Runnable::run);
        GateTxnPay order = ReflectionTestUtils.invokeMethod(service, "buildOrder", request);
        assertNotNull(order);
        assertEquals(adviceOpt, order.getAdviceOpt());
        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(service, "isBomSupplement", order));

        var cfg = new Configuration();
        String resource = "mapper/GateTxnPayMapper.xml";
        try (var in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, cfg, resource, cfg.getSqlFragments()).parse();
        }
        var sql = cfg.getMappedStatement("com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper.insert")
                .getBoundSql(order);
        assertTrue(sql.getSql().contains("ADVICE_OPT"));
        assertTrue(sql.getParameterMappings().stream().anyMatch(p -> p.getProperty().equals("adviceOpt")));
        assertEquals(adviceOpt, cfg.newMetaObject(sql.getParameterObject()).getValue("adviceOpt"));
    }

    /** 真实闸机检票与 APP 自助补站不带 adviceOpt，MUST 照常走后付费扣款。 */
    @ParameterizedTest
    @ValueSource(strings = {"", "018", "999"})
    void nonBomSupplementStillDebits(String adviceOpt) {
        var request = new GateTxnPayReqDTO();
        request.setCardId("0426090949000159");
        request.setCardType("0441");
        request.setHandleDateTime("20260922153642");
        request.setTrxAmount("200");
        request.setAdviceOpt(adviceOpt);
        var service = new GateTxnPayServiceImpl(null, null, null, null, null, null, null, Runnable::run);
        GateTxnPay order = ReflectionTestUtils.invokeMethod(service, "buildOrder", request);
        assertNotNull(order);
        assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeMethod(service, "isBomSupplement", order));
    }
}
