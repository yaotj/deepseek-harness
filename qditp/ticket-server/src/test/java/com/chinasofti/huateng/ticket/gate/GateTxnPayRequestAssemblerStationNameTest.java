package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.ticket.station.StationNameResolver;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住 ADR-D83 续那条行为变更：**进出站站名各自独立回填**。
 *
 * <p>改造前是「进站码为空即整体 return」，于是离线码进站报文未上传（进站码为空 / 占位 {@code FFFF}）时
 * 连出站站名一起丢，列表侧只能回落显示站点编码。这几条用例就是防止那条短路被加回来。</p>
 *
 * <p>夹具刻意不用 Mockito：{@link StationNameResolver} 是字段注入（无构造器参数），
 * 匿名子类覆写 {@code resolveStationNames} 即可，还能顺手断言「问了哪几个码」。</p>
 */
class GateTxnPayRequestAssemblerStationNameTest {

    private final Set<String> askedCodes = new LinkedHashSet<>();

    /** 只认 0245 / 0622 两个码，其余（含 FFFF）一律查不到 —— 与 v41 参数表的真实行为一致。 */
    private GateTxnPayRequestAssembler assembler() {
        StationNameResolver resolver = new StationNameResolver() {
            @Override
            public Map<String, String> resolveStationNames(Set<String> stationCodes) {
                askedCodes.addAll(stationCodes);
                Map<String, String> names = new HashMap<>();
                if (stationCodes.contains("0245")) {
                    names.put("0245", "合川路");
                }
                if (stationCodes.contains("0622")) {
                    names.put("0622", "辛屯");
                }
                return names;
            }
        };
        return new GateTxnPayRequestAssembler(resolver);
    }

    private NotifyVerifyResultReqDTO request(String entryCode, String exitCode) {
        NotifyVerifyResultReqDTO request = new NotifyVerifyResultReqDTO();
        request.setCardId("UT0426090942000095");
        request.setLastHandleStationCode(entryCode);
        request.setHandleStationCode(exitCode);
        return request;
    }

    @Test
    void fillsBothNames() {
        GateTxnPayReqDTO payRequest = assembler()
                .assemble(request("0245", "0622"), new NotifyVerifyResultRespDTO(), null);
        assertEquals("合川路", payRequest.getEntryStationName());
        assertEquals("辛屯", payRequest.getExitStationName());
    }

    @Test
    void blankEntryCodeStillFillsExitName() {
        GateTxnPayReqDTO payRequest = assembler()
                .assemble(request(null, "0622"), new NotifyVerifyResultRespDTO(), null);
        assertNull(payRequest.getEntryStationName());
        assertEquals("辛屯", payRequest.getExitStationName(), "进站码为空 MUST NOT 连出站站名一起丢");
    }

    @Test
    void placeholderEntryCodeStillFillsExitName() {
        GateTxnPayReqDTO payRequest = assembler()
                .assemble(request("FFFF", "0622"), new NotifyVerifyResultRespDTO(), null);
        assertNull(payRequest.getEntryStationName(), "FFFF 查不到站名时 MUST 留 null，NEVER 把编码写进名字列");
        assertEquals("辛屯", payRequest.getExitStationName());
    }

    @Test
    void blankExitCodeStillFillsEntryName() {
        GateTxnPayReqDTO payRequest = assembler()
                .assemble(request("0245", null), new NotifyVerifyResultRespDTO(), null);
        assertEquals("合川路", payRequest.getEntryStationName());
        assertNull(payRequest.getExitStationName());
    }

    @Test
    void bothCodesBlankSkipsQuery() {
        GateTxnPayReqDTO payRequest = assembler()
                .assemble(request(null, "  "), new NotifyVerifyResultRespDTO(), null);
        assertNull(payRequest.getEntryStationName());
        assertNull(payRequest.getExitStationName());
        assertTrue(askedCodes.isEmpty(), "两个码都为空时 MUST NOT 发 RPC");
    }

    @Test
    void sameEntryAndExitStationIsDeduplicated() {
        GateTxnPayReqDTO payRequest = assembler()
                .assemble(request("0622", "0622"), new NotifyVerifyResultRespDTO(), null);
        assertEquals("辛屯", payRequest.getEntryStationName());
        assertEquals("辛屯", payRequest.getExitStationName());
        assertEquals(1, askedCodes.size(), "进出同站 MUST 去重后只问一个码");
    }
}
