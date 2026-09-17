package com.chinasofti.huateng.paysign.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.CheckFailedOrdersRespDTO;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 护栏：审计流水的 RESULT_CODE / RESULT_MSG 对每种应答形状都要有值，含「取不到」与「超长」两个边界。 */
class PaySignAuditResultCodeTest {

    private static final String SEQ = "0052290701523999";
    private static final String USER = "U-TEST-0001";

    private final PaySignRequestMapper mapper = mock(PaySignRequestMapper.class);
    private final PaySignAuditLogger auditLogger = new PaySignAuditLogger(mapper);

    @Test
    void baseRespShapeStillPrefersRetCodeOverCode() {
        BaseRespDTO response = new BaseRespDTO();
        response.setRetCode("0000");
        response.setRetMsg("成功");
        response.setCode(200);
        response.setMsg("OK");

        PaySignRequest saved = writeAndCapture(response);

        assertEquals("0000", saved.getResultCode(), "形状 A MUST 仍取 retCode，NEVER 改成 code（会变既有数据口径）");
        assertEquals("成功", saved.getResultMsg());
    }

    @Test
    void resultCodeShapeIsNoLongerLost() {
        CheckFailedOrdersRespDTO response = new CheckFailedOrdersRespDTO();
        response.setResultCode("9001");
        response.setResultMsg("系统异常");
        response.setHasFailedOrder(false);

        PaySignRequest saved = writeAndCapture(response);

        assertEquals("9001", saved.getResultCode(), "resultCode 形状此前恒为 NULL，这是本次修复的核心");
        assertEquals("系统异常", saved.getResultMsg());
    }

    @Test
    void gatewayShapeFallsBackToCodeAndMsg() {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(600);
        response.setMsg("操作失败");

        PaySignRequest saved = writeAndCapture(response);

        assertEquals("600", saved.getResultCode(), "网关原始应答只有 code/msg，MUST 也能落库");
        assertEquals("操作失败", saved.getResultMsg());
    }

    @Test
    void nullResponseLeavesBothColumnsNull() {
        PaySignRequest saved = writeAndCapture(null);

        assertNull(saved.getResponseBody());
        assertNull(saved.getResultCode());
        assertNull(saved.getResultMsg());
    }

    @Test
    void nonJsonObjectResponseKeepsBodyAndLeavesColumnsNull() {
        PaySignRequest saved = writeAndCapture(List.of("a", "b"));

        assertNotNull(saved.getResponseBody(), "应答不是 JSON 对象时 MUST 仍留全文，NEVER 连证据一起丢");
        assertNull(saved.getResultCode());
        assertNull(saved.getResultMsg());
    }

    @Test
    void oversizedMsgIsTruncatedToColumnWidth() {
        BaseRespDTO response = new BaseRespDTO();
        response.setRetCode("9999");
        response.setRetMsg("X".repeat(2000));

        PaySignRequest saved = writeAndCapture(response);

        assertEquals(1024, saved.getResultMsg().length(),
                "RESULT_MSG 只有 1024 CHAR；不截断时 ORA-12899 会被 catch 吞掉，整行流水都不落库");
    }

    @Test
    void blankRequestSignSeqNeverInserts() {
        BaseRespDTO response = new BaseRespDTO();
        response.setRetCode("0000");

        auditLogger.write("REQUEST_SIGN_INFO", USER, "  ", "03", "ALIPAY", null, response);

        verify(mapper, never()).insert(any(PaySignRequest.class));
    }

    @Test
    void mapperFailureNeverPropagates() {
        when(mapper.insert(any(PaySignRequest.class)))
                .thenThrow(new RuntimeException("ORA-12899: value too large for column"));
        BaseRespDTO response = new BaseRespDTO();
        response.setRetCode("0000");

        auditLogger.write("REQUEST_SIGN_INFO", USER, SEQ, "03", "ALIPAY", null, response);

        verify(mapper).insert(any(PaySignRequest.class));
    }

    private PaySignRequest writeAndCapture(Object response) {
        when(mapper.insert(any(PaySignRequest.class))).thenReturn(1);
        auditLogger.write("REQUEST_SIGN_INFO", USER, SEQ, "03", "ALIPAY", null, response);
        ArgumentCaptor<PaySignRequest> captor = ArgumentCaptor.forClass(PaySignRequest.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }
}
