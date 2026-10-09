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

class GateCompanionFlagPersistenceTest {
    @ParameterizedTest
    @ValueSource(strings = {"N", "Y", "C"})
    void flagReachesOrderAndInsertBindingUnchanged(String flag) throws Exception {
        var request = new GateTxnPayReqDTO();
        request.setCardId("0426090949000159");
        request.setCardType("0441");
        request.setHandleDateTime("20260917153000");
        request.setCompanionFlag(flag);
        var service = new GateTxnPayServiceImpl(null, null, null, null, null, null, null, Runnable::run);
        GateTxnPay order = ReflectionTestUtils.invokeMethod(service, "buildOrder", request);
        assertNotNull(order);
        assertEquals(flag, order.getCompanionFlag());
        var cfg = new Configuration();
        String resource = "mapper/GateTxnPayMapper.xml";
        try (var in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, cfg, resource, cfg.getSqlFragments()).parse();
        }
        var sql = cfg.getMappedStatement("com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper.insert")
                .getBoundSql(order);
        assertTrue(sql.getSql().contains("COMPANION_FLAG"));
        assertTrue(sql.getParameterMappings().stream().anyMatch(p -> p.getProperty().equals("companionFlag")));
        assertEquals(flag, cfg.newMetaObject(sql.getParameterObject()).getValue("companionFlag"));
    }
}
