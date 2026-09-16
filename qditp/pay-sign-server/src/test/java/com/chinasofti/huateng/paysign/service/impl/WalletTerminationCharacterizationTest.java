package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.junit.jupiter.api.Test;

/**
 * {@code requestTermination} <b>钱包（{@code 0B}）分支</b>的特征测试（护栏，2026-09-16）。
 *
 * <p><b>补的是层 3 动工前的最后一块空白</b>：把三个入口的渠道分派改成 sealed 策略之前，
 * 逐条核对了「每个入口的两条分支是否都有断言」，结果是六条里只有这一条没有 ——
 * {@code requestContractAdvisory} 两支都有（{@code ContractDomainCharacterizationTest}），
 * {@code requestContractResult} 两支都有（钱包支在 {@code AccountReadCharacterizationTest} 的 4 条），
 * 而 {@code requestTermination} 只有传统渠道那支，钱包支（{@code releaseWalletBinding}）零覆盖。
 * 按 ADR-D108 续（二）那条「零覆盖的方法 NEVER 先拆」，这个类必须先存在。
 *
 * <p>钉住的不变量：
 * <ul>
 *   <li><b>钱包解绑不建解约申请、不出网调支付中心</b>：它由账户域 {@code requestAgreeRelease}
 *       同步完成，与 {@code APP_TERMINATION_REQUEST} 的 T+4 扫描链路完全无关。
 *       把它误接进解约申请流程会给钱包用户凭空造一条永远等不到回调的 {@code SCANNING} 记录。</li>
 *   <li><b>渠道号 MUST 用 {@code PaymentChannels.walletCode()} 传给账户域</b>，
 *       NEVER 透传请求里的原值（可能带空白或大小写差异）。</li>
 *   <li><b>{@code BizRejected} 与 {@code Unreachable} 分开处置</b>（ADR-D45）：前者把对端文案带出来，
 *       后者只给通用文案并把栈打进日志。两者都 NEVER 报成成功。</li>
 *   <li><b>缺 cardId / cardType 时 NEVER 调账户域</b>：那个端点的参数校验会失败，
 *       等于把一次必然失败的出网当成业务分支。</li>
 * </ul>
 */
class WalletTerminationCharacterizationTest {

    private static final String WALLET_VENDOR = "0B";
    private static final String USER = "U-TEST-0004";
    private static final String SEQ = "0052290701523996";
    private static final String CARD_ID = "CARD-4";
    private static final String CARD_TYPE = "0441";

    @Test
    void walletReleaseSuccessCallsAccountDomainWithCanonicalWalletCode() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.agreeRelease(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Ok());

        RequestTerminationRespDTO result = fixture.service.requestTermination(walletTermination(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.accountDomainPort).agreeRelease(USER, WALLET_VENDOR, CARD_ID, CARD_TYPE);
    }

    /** 钱包解绑与解约申请链路无关：既不落 APP_TERMINATION_REQUEST，也不调支付中心。 */
    @Test
    void walletReleaseNeverCreatesTerminationRequestNorCallsGateway() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.agreeRelease(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Ok());

        fixture.service.requestTermination(walletTermination(), "01");

        verify(fixture.terminationRequestMapper, never()).insert(any());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    /** 业务拒绝：把对端文案带给上游，NEVER 吞成通用错误、更 NEVER 报成功。 */
    @Test
    void walletReleaseBizRejectedPassesRemoteMessageThrough() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.agreeRelease(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.BizRejected("8004", "该卡不存在钱包绑定"));

        RequestTerminationRespDTO result = fixture.service.requestTermination(walletTermination(), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        assertEquals("该卡不存在钱包绑定", result.getRetMsg());
    }

    /** 对端文案缺失时退化成通用文案，NEVER 把 null 透给上游。 */
    @Test
    void walletReleaseBizRejectedWithoutMessageFallsBackToGenericText() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.agreeRelease(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.BizRejected(null, null));

        RequestTerminationRespDTO result = fixture.service.requestTermination(walletTermination(), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        assertEquals("钱包解绑失败", result.getRetMsg());
    }

    /** 不可达：与业务拒绝走不同分支（日志带栈），对上游同样是失败。 */
    @Test
    void walletReleaseUnreachableIsReportedAsFailureNotSuccess() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.agreeRelease(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Unreachable(new RuntimeException("connect timed out")));

        RequestTerminationRespDTO result = fixture.service.requestTermination(walletTermination(), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        assertEquals("钱包解绑失败", result.getRetMsg());
    }

    /** 缺 cardType：本地就该拒掉，NEVER 让一次必然失败的出网发生。 */
    @Test
    void walletReleaseWithoutCardTypeIsRejectedLocally() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        RequestTerminationReqDTO request = walletTermination();
        request.setCardType(null);

        RequestTerminationRespDTO result = fixture.service.requestTermination(request, "01");

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        verify(fixture.accountDomainPort, never())
                .agreeRelease(anyString(), anyString(), anyString(), anyString());
    }

    private RequestTerminationReqDTO walletTermination() {
        RequestTerminationReqDTO request = new RequestTerminationReqDTO();
        request.setThirdUserId(USER);
        request.setRequestSignSeq(SEQ);
        request.setPaymentVendor(WALLET_VENDOR);
        request.setCardId(CARD_ID);
        request.setCardType(CARD_TYPE);
        return request;
    }
}
