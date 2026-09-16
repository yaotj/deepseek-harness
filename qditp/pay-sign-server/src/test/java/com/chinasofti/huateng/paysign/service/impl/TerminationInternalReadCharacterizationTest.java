package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.UnbindAgreementReqDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.port.AccountPayChannelView;
import com.chinasofti.huateng.paysign.port.AccountQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 账户域**读**调用第三处 —— IF8A-75 直接解绑里的
 * {@code accountDomainPort.queryPayChannelByContract} 的特征测试
 *（2026-09-16 前是直连 {@code accountClient.queryPayChannelByContractNo}，见 ADR-D94）。
 *
 * <p>与 {@link AccountReadCharacterizationTest} 合起来覆盖 {@code AccountDomainPort} Javadoc 点名的
 * 三处读调用；那条 Javadoc 的硬前置条件是「要收读 MUST 先给这三处补单测」，本类补的是第三处。
 *
 * <p><b>本类钉住的口径</b>：{@code CARD_ID} / {@code CARD_TYPE} 的**真实来源是账户域**，
 * 不是签约表。`APP_PAY_SIGN_INFO` 的这两列全库为 NULL（签约链路从不写它们），而
 * `APP_TERMINATION_REQUEST` 两列 NOT NULL —— 所以「从账户域取回来塞进入参」这一步一旦丢，
 * 补建解约申请必然 `ORA-01400`（2026-09-08 实测）。
 * {@link #unbindAgreementFillsCardInfoFromAccount()} 就是这条链路的判据，<b>NEVER 删</b>。
 *
 * <p>另一条同等重要：账户域**取不到时不中断**（既不抛也不改码），把入参留空交给下游按原口径处理。
 * 这是刻意的 —— 「取不到辅助信息」不该放大成整个接口不可用。
 */
class TerminationInternalReadCharacterizationTest {

    private static final String SEQ = "0052290701523999";
    private static final String USER = "U-TEST-0001";
    private static final String ALIPAY_VENDOR = "03";
    private static final String ACCOUNT_CARD_ID = "CARD-FROM-ACCOUNT";
    private static final String ACCOUNT_CARD_TYPE = "0441";
    private static final String BIZ_REJECT_CODE = "8004";

    /**
     * 账户域答成功时，{@code CARD_ID} / {@code CARD_TYPE} 取自账户域并落进补建的解约申请。
     *
     * <p>断言落在「补建时 INSERT 了什么」上，而不是「有没有调过账户域」——
     * 后者只证明发了请求，证不出结果被用上了。
     */
    @Test
    @DisplayName("IF8A-75 直接解绑：账户域答成功时用它的 CARD_ID/CARD_TYPE 补建解约申请")
    void unbindAgreementFillsCardInfoFromAccount() {
        TerminationInternalFixture fixture = givenUnbindReady();
        when(fixture.accountDomainPort.queryPayChannelByContract(anyString()))
                .thenReturn(found(ACCOUNT_CARD_ID, ACCOUNT_CARD_TYPE));

        UnbindAgreementResult result = fixture.service.unbindAgreement(unbindRequest());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        AppTerminationRequest inserted = captureInserted(fixture);
        assertEquals(ACCOUNT_CARD_ID, inserted.getCardId());
        assertEquals(ACCOUNT_CARD_TYPE, inserted.getCardType());
        assertEquals("PENDING", inserted.getTerminationStatus());
    }

    /**
     * 账户域**业务拒绝**时不中断：入参维持为空，补建时两列回落到签约记录（实测即 NULL）。
     *
     * <p>这条同时是一份**缺陷存证**：线上 `APP_PAY_SIGN_INFO.CARD_ID` 全为 NULL，因此这条路径
     * 在真库上会 `ORA-01400`。它是「取不到辅助信息不放大成接口不可用」这个取舍的代价，
     * 不是本次重构要改的东西 —— 真要改 MUST 先立 ADR，届时连同本断言一起改。
     */
    @Test
    @DisplayName("IF8A-75 直接解绑：账户域业务拒绝时不中断、不采信，两列回落签约记录")
    void unbindAgreementDoesNotFillCardInfoOnAccountBizRejection() {
        TerminationInternalFixture fixture = givenUnbindReady();
        when(fixture.accountDomainPort.queryPayChannelByContract(anyString())).thenReturn(notFound());

        UnbindAgreementResult result = fixture.service.unbindAgreement(unbindRequest());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        AppTerminationRequest inserted = captureInserted(fixture);
        assertNull(inserted.getCardId());
        assertNull(inserted.getCardType());
    }

    /**
     * 账户域**不可达**同样不中断。
     *
     * <p>收进端口后「不可达」由 {@code Unreachable} 分支表达（异常在
     * {@code AccountDomainRpcAdapter} 里就被收住了），因此这里不再 {@code thenThrow}；
     * 另用 {@link #unbindAgreementSurvivesPortThrowing()} 单独钉住「端口万一真抛了也不外溢」。
     */
    @Test
    @DisplayName("IF8A-75 直接解绑：账户域不可达时不中断")
    void unbindAgreementSurvivesAccountUnreachable() {
        TerminationInternalFixture fixture = givenUnbindReady();
        when(fixture.accountDomainPort.queryPayChannelByContract(anyString())).thenReturn(unreachable());

        UnbindAgreementResult result = fixture.service.unbindAgreement(unbindRequest());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertNull(captureInserted(fixture).getCardId());
    }

    /**
     * 端口实现万一抛了（适配器有 bug、Mockito 配错），异常 **NEVER** 逃出本方法。
     *
     * <p>这条守的是调用点自己的 try/catch。收端口前它由「账户域抛异常」那条用例顺带覆盖，
     * 收端口后契约上端口不抛了，那层 catch 就没人证明还在 —— **NEVER 因为「端口不该抛」删掉本用例**。
     */
    @Test
    @DisplayName("IF8A-75 直接解绑：端口抛异常时不中断，异常不外溢")
    void unbindAgreementSurvivesPortThrowing() {
        TerminationInternalFixture fixture = givenUnbindReady();
        when(fixture.accountDomainPort.queryPayChannelByContract(anyString()))
                .thenThrow(new RuntimeException("connect timed out"));

        UnbindAgreementResult result = fixture.service.unbindAgreement(unbindRequest());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertNull(captureInserted(fixture).getCardId());
    }

    /** 钱包渠道在本接口上直接短路成 8001，**绝不**触及账户域与支付网关。 */
    @Test
    @DisplayName("IF8A-75 直接解绑：钱包渠道短路成 8001，不查账户域")
    void unbindAgreementShortCircuitsWalletVendor() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();

        UnbindAgreementReqDTO request = unbindRequest();
        request.setPaymentVendor("0B");
        UnbindAgreementResult result = fixture.service.unbindAgreement(request);

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        verify(fixture.accountDomainPort, org.mockito.Mockito.never()).queryPayChannelByContract(anyString());
    }

    // ---------------------------------------------------------------------
    // 夹具
    // ---------------------------------------------------------------------

    /**
     * 把「解约申请尚不存在、签约记录存在且两列为 NULL、CAS 抢到 PENDING、网关答成功」这条
     * 主路径铺好 —— 只有这条路径会真的走到 {@code fillCardInfoFromAccount} 的结果。
     */
    private TerminationInternalFixture givenUnbindReady() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(null);
        PaySignInfo signInfo = new PaySignInfo();
        signInfo.setThirdUserId(USER);
        signInfo.setPaymentVendor(ALIPAY_VENDOR);
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, ALIPAY_VENDOR)).thenReturn(signInfo);
        when(fixture.terminationRequestMapper.markScanning(anyString(), any())).thenReturn(1);
        PaySignGatewayResponse gateway = new PaySignGatewayResponse();
        gateway.setCode(0);
        gateway.setMsg("成功");
        when(fixture.contractDomainService.requestPayPlatformTermination(SEQ)).thenReturn(gateway);
        when(fixture.payGatewayClient.isSuccess(any())).thenCallRealMethod();
        return fixture;
    }

    private AppTerminationRequest captureInserted(TerminationInternalFixture fixture) {
        ArgumentCaptor<AppTerminationRequest> captor = ArgumentCaptor.forClass(AppTerminationRequest.class);
        verify(fixture.terminationRequestMapper).insert(captor.capture());
        return captor.getValue();
    }

    /** 账户域答了成功 —— 端口已判过 retCode，落到 {@code Found} 即可采信。 */
    private AccountQuery<AccountPayChannelView> found(String cardId, String cardType) {
        return new AccountQuery.Found<>(new AccountPayChannelView(cardId, cardType, USER));
    }

    /** 账户域业务拒绝（如 {@code 8004}）或响应为空 —— **NEVER 让它带上 cardId**，见 ADR-D94。 */
    private AccountQuery<AccountPayChannelView> notFound() {
        return new AccountQuery.NotFound<>(BIZ_REJECT_CODE, "该用户无此支付通道");
    }

    /** 账户域不可达 —— 与业务拒绝必须是两个分支。 */
    private AccountQuery<AccountPayChannelView> unreachable() {
        return new AccountQuery.Unreachable<>(new RuntimeException("connect timed out"));
    }

    private UnbindAgreementReqDTO unbindRequest() {
        UnbindAgreementReqDTO request = new UnbindAgreementReqDTO();
        request.setThirdUserId(USER);
        request.setPaymentVendor(ALIPAY_VENDOR);
        request.setRequestSignSeq(SEQ);
        return request;
    }
}
