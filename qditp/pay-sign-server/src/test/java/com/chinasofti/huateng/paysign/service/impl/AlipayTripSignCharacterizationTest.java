package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code alipayTripRequestSignInfo}（支付宝出行签约）的特征测试（护栏，2026-09-16）。
 *
 * <p><b>此前完全零覆盖</b>（ADR-D110 续清单第 3 条）。它是全模块唯一
 * <b>不经支付中心、直接把 {@code APP_PAY_SIGN_INFO} 写成 {@code SIGNED}</b> 的入口 ——
 * 渠道侧已经签好，我方只做同步确认。因此这里没有网关调用、也没有回调收口，
 * 一旦放宽已签约校验就会出现同一用户同渠道两行签约记录。
 *
 * <p><b>本类同时钉住一个「现状」而非「应然」</b>：一次成功请求会往
 * {@code APP_PAY_SIGN_REQUEST} 写 <b>两行</b>流水 ——
 * <ul>
 *   <li>方法体内 :275~288 手写的一行：{@code OPERATION_TYPE='ALIPAY_TRIP_REQUEST_SIGN_INFO'}（原样字面量）、
 *       {@code SIGN_STATUS='SIGNED'}，无请求/响应报文；</li>
 *   <li>收尾 {@code auditAlipayTripSignInfo} 经 {@code PaySignAuditLogger} 写的一行：
 *       {@code OPERATION_TYPE='SIGN'}（被 {@code convertOperationType} 归并过）、带报文与 {@code RESULT_CODE}、
 *       {@code SIGN_STATUS} 为空。</li>
 * </ul>
 * 两行互补但确实是两行，而 ADR-D107 那轮「审计流水按接口收口」<b>漏掉了这处手写点</b> ——
 * 它也是 {@code paySignRequestMapper} 在 {@code ContractDomainServiceImpl} 里仅存的引用（全类第 288 行一处）。
 * <b>要不要合并成一行属于改审计口径（行数与 OPERATION_TYPE 都会变），MUST 由人裁决，
 * 本类先把现状钉住</b>：合并那天这两条断言会变红，那正是它们的用途。
 */
class AlipayTripSignCharacterizationTest {

    private static final String USER = "U-TEST-0007";
    private static final String AGREEMENT_CODE = "2088ALIPAYTRIP0001";
    private static final String ALIPAY_TRIP_VENDOR = "05";

    /** 同步确认：直接落 SIGNED，且 NEVER 调支付中心。 */
    @Test
    void writesSignedRecordLocallyWithoutCallingPayCenter() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectByUserAndVendor(USER, ALIPAY_TRIP_VENDOR)).thenReturn(null);

        RequestSignInfoResult result = fixture.service.alipayTripRequestSignInfo(request());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        ArgumentCaptor<PaySignInfo> captor = ArgumentCaptor.forClass(PaySignInfo.class);
        verify(fixture.paySignInfoMapper).insert(captor.capture());
        PaySignInfo inserted = captor.getValue();
        assertEquals(AGREEMENT_CODE, inserted.getRequestSignSeq());
        assertEquals(USER, inserted.getThirdUserId());
        assertEquals(ALIPAY_TRIP_VENDOR, inserted.getPaymentVendor());
        assertEquals("ALIPAY", inserted.getSignChannel());
        assertEquals("SIGNED", inserted.getContractStatus());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    /** 本接口不返回 SDK 参数：渠道侧已签好，APP 无需再唤起。 */
    @Test
    void neverReturnsSdkInfo() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectByUserAndVendor(USER, ALIPAY_TRIP_VENDOR)).thenReturn(null);

        RequestSignInfoResult result = fixture.service.alipayTripRequestSignInfo(request());

        assertNull(result.getRequestStartSdkInfo());
    }

    /** 已签约即拒：放宽会出现同一用户同渠道两行签约记录。 */
    @Test
    void alreadySignedIsRejectedWithoutWriting() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignInfo existing = new PaySignInfo();
        existing.setRequestSignSeq(AGREEMENT_CODE);
        when(fixture.paySignInfoMapper.selectByUserAndVendor(USER, ALIPAY_TRIP_VENDOR)).thenReturn(existing);

        RequestSignInfoResult result = fixture.service.alipayTripRequestSignInfo(request());

        assertEquals(PaySignErrorCodeEnum.ALREADY_SIGNED.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper, never()).insert(any());
    }

    /**
     * 现状：成功一次写两行流水，形状各不相同。
     *
     * <p>手写那行带 {@code SIGN_STATUS='SIGNED'} 与原样 {@code OPERATION_TYPE}；
     * 审计那行 {@code OPERATION_TYPE} 已被归并成 {@code SIGN}。
     */
    @Test
    void successWritesTwoAuditRowsWithDifferentShapes() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectByUserAndVendor(USER, ALIPAY_TRIP_VENDOR)).thenReturn(null);

        fixture.service.alipayTripRequestSignInfo(request());

        List<PaySignRequest> logs = fixture.auditLogs();
        assertEquals(2, logs.size(), "现状是两行；合并成一行是改审计口径，MUST 先有人裁决");

        PaySignRequest handWritten = logs.stream()
                .filter(row -> "ALIPAY_TRIP_REQUEST_SIGN_INFO".equals(row.getOperationType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少方法体内手写的那行流水"));
        assertEquals("SIGNED", handWritten.getSignStatus());
        assertNull(handWritten.getRequestBody(), "手写那行不带报文");

        PaySignRequest audited = logs.stream()
                .filter(row -> "SIGN".equals(row.getOperationType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 PaySignAuditLogger 归并后的那行流水"));
        // 审计那行带报文，但 **RESULT_CODE / RESULT_MSG 为空**：PaySignAuditLogger 只在
        // response instanceof BaseRespDTO 时才回填这两列，而 RequestSignInfoResult 不是 BaseRespDTO 的子类。
        // 后果是「按 RESULT_CODE 捞失败流水」在本接口上恒为空 —— 现状，不是本轮要改的东西。
        assertNull(audited.getResultCode(), "现状：RequestSignInfoResult 不是 BaseRespDTO，取不到 retCode");
        assertNull(audited.getResultMsg());
    }

    /** 失败分支同样留痕（只有审计那一行）。 */
    @Test
    void rejectedRequestStillLeavesAuditRow() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignInfo existing = new PaySignInfo();
        when(fixture.paySignInfoMapper.selectByUserAndVendor(USER, ALIPAY_TRIP_VENDOR)).thenReturn(existing);

        fixture.service.alipayTripRequestSignInfo(request());

        List<PaySignRequest> logs = fixture.auditLogs();
        assertEquals(1, logs.size());
        assertEquals("SIGN", logs.get(0).getOperationType());
        // 同上：失败流水也拿不到 8013，运维只能从 RESPONSE_BODY 里看。
        assertNull(logs.get(0).getResultCode());
    }

    private AlipayTripAddContractReqDTO request() {
        AlipayTripAddContractReqDTO request = new AlipayTripAddContractReqDTO();
        request.setThirdUserId(USER);
        request.setAgreementCode(AGREEMENT_CODE);
        request.setChannel(ALIPAY_TRIP_VENDOR);
        request.setChannelUserAccount("138****8000");
        return request;
    }
}
