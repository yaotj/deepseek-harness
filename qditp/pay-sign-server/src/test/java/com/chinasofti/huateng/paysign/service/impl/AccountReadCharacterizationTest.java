package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.port.AccountQuery;
import com.chinasofti.huateng.paysign.port.AccountUserView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/** 护栏：账户域读调用 MUST SUCCESS 才采信，业务拒绝与不可达分两支（ADR-D94，防拿失败应答去扣款）。 */
class AccountReadCharacterizationTest {

    private static final String WALLET_VENDOR = "0B";
    private static final String ALIPAY_VENDOR = "03";
    private static final String USER = "U-TEST-0001";
    private static final String SEQ = "0052290701523999";
    private static final String ORDER = "GT20260916000000001";
    private static final String PAY_ID = "2088-WALLET-0001";
    private static final String BIZ_REJECT_CODE = "8004";

    /** 三条件齐备（SUCCESS + channel=0B + thirdPayId 非空）才算已绑定。 */
    @Test
    @DisplayName("钱包绑定查询：账户域 SUCCESS 且通道为 0B 时回 SIGNED 并带出 thirdPayId")
    void walletBindingResultReturnsSignedWhenAccountConfirms() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.queryUser(anyString(), anyString(), anyString()))
                .thenReturn(found(WALLET_VENDOR, PAY_ID, null));

        RequestContractResultRespDTO result =
                fixture.service.requestContractResult(walletQuery(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("SIGNED", result.getStatus());
        assertEquals(PAY_ID, result.getPayUserId());
        assertEquals(PAY_ID, result.getPayAccountId());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    /** 账户域**业务拒绝**时回 NOT_SIGNED，且**不带出** thirdPayId。 */
    @Test
    @DisplayName("钱包绑定查询：账户域业务拒绝时回 NOT_SIGNED，且不回填 thirdPayId")
    void walletBindingResultTreatsBizRejectionAsNotSigned() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.queryUser(anyString(), anyString(), anyString()))
                .thenReturn(notFound());

        RequestContractResultRespDTO result =
                fixture.service.requestContractResult(walletQuery(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("NOT_SIGNED", result.getStatus());
        assertNull(result.getPayUserId());
        assertNull(result.getPayAccountId());
    }

    /** 账户域返 null（对端契约异常）同样收成 NOT_SIGNED，NEVER 抛给 APP。 */
    @Test
    @DisplayName("钱包绑定查询：账户域响应为空时回 NOT_SIGNED")
    void walletBindingResultTreatsNullResponseAsNotSigned() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.queryUser(anyString(), anyString(), anyString())).thenReturn(notFound());

        RequestContractResultRespDTO result =
                fixture.service.requestContractResult(walletQuery(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("NOT_SIGNED", result.getStatus());
    }

    /** 账户域**不可达**（抛异常）收成 9001，与「业务拒绝」不同码。 */
    @Test
    @DisplayName("钱包绑定查询：账户域不可达时回 9001，与业务拒绝的 0000/NOT_SIGNED 不同码")
    void walletBindingResultMapsAccountUnreachableToSystemError() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.queryUser(anyString(), anyString(), anyString()))
                .thenReturn(unreachable());

        RequestContractResultRespDTO result =
                fixture.service.requestContractResult(walletQuery(), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        assertNull(result.getStatus());
    }

    /** 账户域 SUCCESS 时才用它补齐 {@code paymentVendor} / {@code requestSignSeq}，补齐后能过签约校验。 */
    @Test
    @DisplayName("免密扣款补签约信息：账户域 SUCCESS 时补齐 vendor/seq 并继续走到支付网关")
    void resolvePaySignInfoFillsVendorAndSeqOnAccountSuccess() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.selectByOrderNo(ORDER)).thenReturn(null);
        when(fixture.accountDomainPort.queryUser(anyString(), anyString(), anyString()))
                .thenReturn(found(ALIPAY_VENDOR, null, SEQ));
        fixture.gatewayReturns(PaySignFacadeFixture.gatewayFailure(600, "操作失败"));

        RequestPayResult result = fixture.service.requestPay(payRequestMissingSignInfo());

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        verify(fixture.payGatewayClient, atLeastOnce()).request(anyString(), anyMap());
    }

    /** 账户域**业务拒绝**时 NEVER 采信 DTO 里残留的 {@code channel} / {@code reqContractNo}。 */
    @Test
    @DisplayName("免密扣款补签约信息：账户域业务拒绝时不采信残留字段，拦成 8011 且绝不出网")
    void resolvePaySignInfoIgnoresAccountBizRejection() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.selectByOrderNo(ORDER)).thenReturn(null);
        when(fixture.accountDomainPort.queryUser(anyString(), anyString(), anyString()))
                .thenReturn(notFound());

        RequestPayResult result = fixture.service.requestPay(payRequestMissingSignInfo());

        assertEquals(PaySignErrorCodeEnum.USER_NOT_SIGNED.getCode(), result.getRetCode());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    /** 账户域不可达时同样拦成 8011、不出网（原实现靠 catch 兜住，修复后不变）。 */
    @Test
    @DisplayName("免密扣款补签约信息：账户域不可达时拦成 8011 且绝不出网")
    void resolvePaySignInfoStopsWhenAccountUnreachable() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.selectByOrderNo(ORDER)).thenReturn(null);
        when(fixture.accountDomainPort.queryUser(anyString(), anyString(), anyString()))
                .thenReturn(unreachable());

        RequestPayResult result = fixture.service.requestPay(payRequestMissingSignInfo());

        assertEquals(PaySignErrorCodeEnum.USER_NOT_SIGNED.getCode(), result.getRetCode());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    /** 账户域答了成功 —— 端口把「必须 SUCCESS 才采信」判完了，落到 {@code Found} 就意味着可采信。 */
    private AccountQuery<AccountUserView> found(String channel, String thirdPayId, String reqContractNo) {
        return new AccountQuery.Found<>(new AccountUserView(channel, thirdPayId, reqContractNo));
    }

    /** 账户域**业务拒绝**（如 {@code 8004}）或响应为空。 */
    private AccountQuery<AccountUserView> notFound() {
        return new AccountQuery.NotFound<>(BIZ_REJECT_CODE, "该用户无此支付通道");
    }

    /** 账户域**不可达** —— 与业务拒绝必须是两个分支，见类注释。 */
    private AccountQuery<AccountUserView> unreachable() {
        return new AccountQuery.Unreachable<>(new RuntimeException("connect timed out"));
    }

    private RequestContractResultReqDTO walletQuery() {
        RequestContractResultReqDTO request = new RequestContractResultReqDTO();
        request.setThirdUserId(USER);
        request.setRequestSignSeq(SEQ);
        request.setPaymentVendor(WALLET_VENDOR);
        request.setCardId("CARD-1");
        request.setCardType("0441");
        return request;
    }

    /** 缺 {@code paymentVendor} 与 {@code requestSignSeq} 的扣款请求 —— 这正是。 */
    private RequestPayReqDTO payRequestMissingSignInfo() {
        RequestPayReqDTO request = new RequestPayReqDTO();
        request.setOrderNo(ORDER);
        request.setScene("GATE");
        request.setThirdUserId(USER);
        request.setAmount(200);
        request.setIndustryType("METRO");
        request.setSubject("地铁乘车");
        request.setBody("地铁乘车扣费");
        request.setCardId("CARD-1");
        request.setCardType("0441");
        return request;
    }
}
