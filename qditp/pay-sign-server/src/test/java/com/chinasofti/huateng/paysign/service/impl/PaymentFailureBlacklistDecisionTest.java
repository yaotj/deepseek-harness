package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * IT-087：出站免密扣款被支付平台明确业务拒绝时的加黑决策，统一收口在 pay-sign 一处。
 *
 * <p>判据以「支付中心真实订单状态」三态为准（用户 2026-11-20 裁定）：
 * <ul>
 *   <li>SUCCESS（payQuery 明确已成功/已结清）→ 幂等/重复扣款，MUST 免黑；</li>
 *   <li>FAIL（payQuery 明确失败，或查无签约/查无数据——业务结论钱没付成，含 IT-079）→ 加黑；</li>
 *   <li>UNKNOWN（payQuery 未配置 / 异常 / 无业务应答——技术故障问不出结果）→ 保守不加黑。</li>
 * </ul>
 * 渠道边界：本路只覆盖 pay-sign（地铁/云闪付 07 等）；alipay-pay-sign 按渠道独立保留，不在本测试范围。
 */
class PaymentFailureBlacklistDecisionTest {

    private static final String ORDER_NO = "GT2026112008405000000000508";
    private static final String CARD_ID = "0426090949000508";
    private static final String PAY_URL = PaySignFacadeFixture.GATEWAY_URL_PREFIX + "pay";
    private static final String QUERY_URL = PaySignFacadeFixture.GATEWAY_URL_PREFIX + "pay/query";

    private final PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

    @BeforeEach
    void setUp() {
        PayTxnDetail existing = new PayTxnDetail();
        existing.setOrderNo(ORDER_NO);
        existing.setPayStatus("INIT");
        when(fixture.payTxnDetailMapper.selectByOrderNo(ORDER_NO)).thenReturn(existing);
        when(fixture.payTxnDetailMapper.markRequesting(ORDER_NO)).thenReturn(1);
        when(fixture.blacklistClient.addBlackList(any())).thenReturn(new BlackListOperateResult());
    }

    /** 查无签约（payQuery 回 9999 未找到数据，业务应答非空）→ 归 FAIL → 加黑（IT-079 场景）。 */
    @Test
    void contractNotFoundByGatewayIsTreatedAsFailAndBlacklisted() {
        when(fixture.payGatewayClient.request(eq(PAY_URL), anyMap()))
                .thenReturn(gateway(9001, "未查到相关信息[3910014]"));
        when(fixture.payGatewayClient.request(eq(QUERY_URL), anyMap()))
                .thenReturn(gateway(9999, "未找到数据")); // payQuery 业务应答「查无」→ FAIL

        fixture.paymentDomainService.requestPay(validRequest("07"));

        ArgumentCaptor<AddBlackListReqDTO> captor = ArgumentCaptor.forClass(AddBlackListReqDTO.class);
        verify(fixture.blacklistClient).addBlackList(captor.capture());
        AddBlackListReqDTO req = captor.getValue();
        assertEquals(CARD_ID, req.getCardId());
        assertEquals("01", req.getChannelCode(), "channel=01 走闸机欠费源，补扣成功后可被 job 230 自动解除");
        assertEquals("01", req.getBlackCause());
        assertEquals("01", req.getBlackSource());
        assertEquals(ORDER_NO, req.getBizNo());
        assertTrue(req.getReason().contains("9001") && req.getReason().contains("3910014"),
                "REASON MUST 携带失败原因 code+msg，实际=" + req.getReason());
    }

    /** 二次确认 SUCCESS（已付费/幂等拒答）→ 免黑。 */
    @Test
    void confirmedSuccessIsIdempotentNotBlacklisted() {
        when(fixture.payGatewayClient.request(eq(PAY_URL), anyMap()))
                .thenReturn(gateway(600, "订单已支付成功，请勿重复支付"));
        when(fixture.payGatewayClient.request(eq(QUERY_URL), anyMap()))
                .thenReturn(queryStatus("SUCCESS"));

        fixture.paymentDomainService.requestPay(validRequest("07"));

        verify(fixture.blacklistClient, never()).addBlackList(any());
    }

    /** 二次确认 FAIL（明确扣款失败）→ 加黑。 */
    @Test
    void confirmedFailStatusAddsBlacklist() {
        when(fixture.payGatewayClient.request(eq(PAY_URL), anyMap()))
                .thenReturn(gateway(600, "操作失败"));
        when(fixture.payGatewayClient.request(eq(QUERY_URL), anyMap()))
                .thenReturn(queryStatus("FAIL"));

        fixture.paymentDomainService.requestPay(validRequest("03"));

        verify(fixture.blacklistClient).addBlackList(any());
    }

    /** 技术故障（payQuery 未配置/无业务应答，UNKNOWN）→ 保守不加黑。 */
    @Test
    void technicalUnknownIsConservativelyNotBlacklisted() {
        when(fixture.payGatewayClient.request(eq(PAY_URL), anyMap()))
                .thenReturn(gateway(9001, "未查到相关信息[3910014]"));
        // payQuery 未 stub → 走 fixture 默认 mock（返回 null）→ 技术故障，UNKNOWN

        fixture.paymentDomainService.requestPay(validRequest("07"));

        verify(fixture.blacklistClient, never()).addBlackList(any());
    }

    /** vendor 空 → 根本不进入加黑判定。 */
    @Test
    void blankVendorDoesNotAddBlacklist() {
        when(fixture.payGatewayClient.request(eq(PAY_URL), anyMap()))
                .thenReturn(gateway(9001, "未查到相关信息"));

        fixture.paymentDomainService.requestPay(validRequest(null));

        verify(fixture.blacklistClient, never()).addBlackList(any());
    }

    private RequestPayReqDTO validRequest(String vendor) {
        RequestPayReqDTO request = new RequestPayReqDTO();
        request.setOrderNo(ORDER_NO);
        request.setScene("AGM_GATE");
        request.setThirdUserId("00523018");
        request.setAmount(400);
        request.setIndustryType("1");
        request.setSubject("地铁乘车扣费");
        request.setBody("地铁乘车费用");
        request.setCardId(CARD_ID);
        request.setCardType("01");
        request.setPaymentVendor(vendor);
        // 非钱包渠道（03/05/07…）走 Contracted，validatePaySignInfo 要求 requestSignSeq 非空，否则 8011 提前返回
        request.setRequestSignSeq("0052301801524730");
        return request;
    }

    /** 支付网关业务应答（code 非成功 = 非 200/0，走 Rejected 分支；raw 非空 → 归 FAIL）。 */
    private PaySignGatewayResponse gateway(int code, String msg) {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }

    /** payQuery 成功应答（code=0/200，走 Accepted 分支），data.status 为指定值。 */
    private PaySignGatewayResponse queryStatus(String status) {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(0);
        response.setMsg("成功");
        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        response.setData(data);
        return response;
    }
}
