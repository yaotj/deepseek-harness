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

/**
 * 账户域**读**调用（{@code accountClient.queryUserInfo}）两处调用点的特征测试。
 *
 * <p><b>为什么单独立一个类</b>：{@code AccountDomainPort} 的 Javadoc 原先写着「刻意只收写、不收读」，
 * 并附了一条硬前置条件 ——「要收读 MUST 先给这三处补单测，NEVER 顺手一起改」。本类就是那三处里
 * 走 {@code queryUserInfo} 的两处（第三处 {@code queryPayChannelByContractNo} 在
 * {@link TerminationInternalReadCharacterizationTest}）。<b>先有这些断言，收端口才是可验证的搬迁</b>。
 *
 * <p><b>本类钉住的核心口径是「retCode 要不要看」</b> —— 两个调用点原先的答案**不一致**：
 * <ul>
 *   <li>{@code ContractDomainServiceImpl.queryWalletBindingResult} 要求
 *       {@code retCode=0000} 才认这条账户数据（`active` 三条件之一）；</li>
 *   <li>{@code PaymentDomainServiceImpl.resolvePaySignInfoFromAccount} <b>完全不看 retCode</b>，
 *       只要 {@code channel} 非空就往支付入参上写。</li>
 * </ul>
 * 后者是缺陷而非设计：账户域返业务失败（如 {@code 8004} 该用户无此通道）时，DTO 里残留的
 * {@code channel} / {@code reqContractNo} 会被当成有效签约信息**送去真实扣款**。已按 ADR-D94
 * 对齐成「必须 SUCCESS 才采信」，{@link #resolvePaySignInfoIgnoresAccountBizRejection()} 即该修复的判据。
 * <b>NEVER 放宽那条断言</b> —— 放宽等于允许拿一份业务失败应答去发起免密扣款。
 */
class AccountReadCharacterizationTest {

    private static final String WALLET_VENDOR = "0B";
    private static final String ALIPAY_VENDOR = "03";
    private static final String USER = "U-TEST-0001";
    private static final String SEQ = "0052290701523999";
    private static final String ORDER = "GT20260916000000001";
    private static final String PAY_ID = "2088-WALLET-0001";
    private static final String BIZ_REJECT_CODE = "8004";

    // ---------------------------------------------------------------------
    // 站点一：ContractDomainServiceImpl.queryWalletBindingResult
    // ---------------------------------------------------------------------

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

    /**
     * 账户域**业务拒绝**时回 NOT_SIGNED，且**不带出** thirdPayId。
     *
     * <p>这条钉的是「`retCode` 是 active 判定的一部分」：拿掉它，一条 {@code 8004}
     * 应答里残留的 {@code thirdPayId} 会被当成有效绑定回给 APP。
     */
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

    /**
     * 账户域**不可达**（抛异常）收成 9001，与「业务拒绝」不同码。
     *
     * <p>两者必须可区分：不可达可重试，业务拒绝重试一万次也不会变。
     */
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

    // ---------------------------------------------------------------------
    // 站点二：PaymentDomainServiceImpl.resolvePaySignInfoFromAccount
    // ---------------------------------------------------------------------

    /**
     * 账户域 SUCCESS 时才用它补齐 {@code paymentVendor} / {@code requestSignSeq}，补齐后能过签约校验。
     *
     * <p>断言落在「有没有走到网关」上：补齐成功 ⇒ 通过 {@code validatePaySignInfo} ⇒ 出网。
     * 用 {@code atLeastOnce()} 而非 {@code times(1)} 是实测口径：网关返失败后
     * {@code requestPay} 还会再查一次支付状态，单笔请求出网 **2 次**。
     * <b>NEVER 改成 {@code times(1)}</b> —— 那不是本测试要钉的东西，会在无关改动上假红。
     */
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

    /**
     * 账户域**业务拒绝**时 NEVER 采信 DTO 里残留的 {@code channel} / {@code reqContractNo}。
     *
     * <p><b>这是 ADR-D94 修复的判据</b>。修复前本方法完全不看 {@code retCode}，于是一条
     * {@code 8004}（该用户无此支付通道）应答里带的 {@code channel=03} + {@code reqContractNo}
     * 会被写进支付入参，随后**真的去支付中心发起免密扣款** —— 拿一份「查不到」的应答扣钱。
     * 修复后这里在 {@code validatePaySignInfo} 拦成 {@code 8011}，出网次数为 0。
     *
     * <p><b>NEVER 把断言放宽成「只要不报错就行」</b>：它守的是资金安全，不是返回码美观。
     */
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

    // ---------------------------------------------------------------------
    // 夹具
    // ---------------------------------------------------------------------

    /** 账户域答了成功 —— 端口把「必须 SUCCESS 才采信」判完了，落到 {@code Found} 就意味着可采信。 */
    private AccountQuery<AccountUserView> found(String channel, String thirdPayId, String reqContractNo) {
        return new AccountQuery.Found<>(new AccountUserView(channel, thirdPayId, reqContractNo));
    }

    /**
     * 账户域**业务拒绝**（如 {@code 8004}）或响应为空。
     *
     * <p><b>NEVER 让本方法带上 channel / thirdPayId</b>：{@code NotFound} 里刻意没有数据槽位，
     * 「拒绝了还能读到残留字段」正是 ADR-D94 那个缺陷的形状，类型上就不该表达得出来。
     */
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

    /**
     * 缺 {@code paymentVendor} 与 {@code requestSignSeq} 的扣款请求 —— 这正是
     * {@code resolvePaySignInfoFromAccount} 的唯一触发条件（且 {@code PAY_TXN_DETAIL} 不存在）。
     */
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
