package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 护栏：IF8A-22 拿到支付平台状态后的落库分支（ContractDomainServiceImpl 私有的 applyGatewayStatus）。
 *
 * <p>ContractResultCharacterizationTest 已钉住主干六条，但那六条里只经过本分支的
 * 「SIGNED 且 CAS 命中」一支；其余六支（同状态补字段 / 目标态为空 / UNSIGNED / NOT_SIGNED /
 * 未知状态 / CAS 落空的两种结局）此前一条都没有。
 *
 * <p>为什么这些分支值得单独钉：它们决定「支付中心说的状态要不要写进 APP_PAY_SIGN_INFO」。
 * 写错方向的后果不对称 —— 多写一次 UNSIGNED 会让在用用户的免密扣款直接失效；
 * 漏写一次会让已解约用户继续被扣。而这两种都不抛异常、接口一律返 0000，
 * 只能靠「哪个 mapper 方法被调了」来判定，所以断言 MUST 落在 mapper 交互上，
 * NEVER 只看 retCode。
 */
class ContractGatewayStatusApplyTest {

    private static final String SEQ = "0052290701523999";
    private static final String USER = "U-TEST-0001";
    private static final String ALIPAY_VENDOR = "03";
    private static final String METRO_APP = "01";

    /** 分支 1：目标态与原状态相同 ⇒ 只补 payAccountId / payAgreementNo，NEVER 走任何状态迁移。 */
    @Test
    void sameStatusOnlyPatchesIdsAndNeverMigrates() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord("SIGNED"));
        fixture.gatewayReturns(gatewayData("SIGNED"));

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), METRO_APP);

        assertEquals("PA-9", result.getPayAccountId());
        assertEquals("AGR-9", result.getPayAgreementNo());
        verify(fixture.paySignInfoMapper).updateBySeq(any(PaySignInfo.class));
        verifyNoStatusMigration(fixture);
    }

    /**
     * 分支 2：网关 data 里没有 status ⇒ 目标态为 null，同样只补字段。
     *
     * <p>与分支 1 不是同一条判断：那条是「相等」，这条是「压根取不到」。
     * 把取不到当成某个具体状态去迁移，就是凭对端漏字段改我方状态机。
     */
    @Test
    void missingStatusInGatewayDataOnlyPatchesIds() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord(null));
        PaySignGatewayResponse response = PaySignFacadeFixture.gatewaySuccess();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("payUserId", "PA-9");
        data.put("payAgreementNo", "AGR-9");
        response.setData(data);
        fixture.gatewayReturns(response);

        fixture.service.requestContractResult(query(), METRO_APP);

        verify(fixture.paySignInfoMapper).updateBySeq(any(PaySignInfo.class));
        verifyNoStatusMigration(fixture);
    }

    /** 分支 3：平台返 UNSIGNED ⇒ 走 markUnsigned，NEVER 走 markSigned。 */
    @Test
    void unsignedFromGatewayGoesThroughMarkUnsigned() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord("SIGNED"));
        fixture.gatewayReturns(gatewayData("UNSIGNED"));
        when(fixture.paySignInfoMapper.markUnsigned(anyString(), any())).thenReturn(1);

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), METRO_APP);

        assertEquals("UNSIGNED", result.getStatus());
        verify(fixture.paySignInfoMapper).markUnsigned(anyString(), any());
        verify(fixture.paySignInfoMapper, never()).markSigned(anyString(), anyString(), anyString(), any());
        verify(fixture.paySignInfoMapper, never()).updateBySeq(any(PaySignInfo.class));
    }

    /** 分支 4：平台返 NOT_SIGNED ⇒ 走 reactivateForResign（重签用），不是 markUnsigned。 */
    @Test
    void notSignedFromGatewayGoesThroughReactivateForResign() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord("UNSIGNED"));
        fixture.gatewayReturns(gatewayData("NOT_SIGNED"));
        when(fixture.paySignInfoMapper.reactivateForResign(SEQ)).thenReturn(1);

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), METRO_APP);

        assertEquals("NOT_SIGNED", result.getStatus());
        verify(fixture.paySignInfoMapper).reactivateForResign(SEQ);
        verify(fixture.paySignInfoMapper, never()).markUnsigned(anyString(), any());
    }

    /**
     * 分支 5：平台返一个我方不认识的状态 ⇒ 只告警、一个字段都不落库，但仍照原样返给上游。
     *
     * <p><b>NEVER 改成「未知就按失败写库」或「未知就当 UNSIGNED」</b>：
     * 对端加一个新状态码是常态，猜错方向就是拿对端的新枚举值改我方签约主表。
     */
    @Test
    void unknownGatewayStatusIsNeverPersisted() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord("SIGNED"));
        fixture.gatewayReturns(gatewayData("PROCESSING_NEW_CODE"));

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), METRO_APP);

        assertEquals("PROCESSING_NEW_CODE", result.getStatus(), "未知状态 MUST 原样返回，便于对账时发现契约漂移");
        verify(fixture.paySignInfoMapper, never()).updateBySeq(any(PaySignInfo.class));
        verifyNoStatusMigration(fixture);
    }

    /**
     * 分支 6：CAS 落空且库里不是目标态 ⇒ 只告警，且**把回查到的真实状态回填进应答**。
     *
     * <p>回填这一步是本分支的要点：库里没被改动，应答就 MUST 说库里的实际状态，
     * NEVER 把「我本来想迁到哪」当成结果返回 —— 那会让上游以为已解约。
     */
    @Test
    void casMissWithWhitelistConflictAnswersObservedStatus() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord("SIGNED"));
        fixture.gatewayReturns(gatewayData("UNSIGNED"));
        when(fixture.paySignInfoMapper.markUnsigned(anyString(), any())).thenReturn(0);
        when(fixture.paySignInfoMapper.selectSignStatusBySeq(SEQ)).thenReturn("SIGN_FAILED");

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), METRO_APP);

        assertEquals("SIGN_FAILED", result.getStatus(), "CAS 落空 MUST 答库里的真实状态");
    }

    /** 分支 7：CAS 落空但库里已是目标态 ⇒ 幂等，应答与目标态一致。 */
    @Test
    void casMissAlreadyAtTargetIsTreatedAsIdempotent() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord("SIGNED"));
        fixture.gatewayReturns(gatewayData("UNSIGNED"));
        when(fixture.paySignInfoMapper.markUnsigned(anyString(), any())).thenReturn(0);
        when(fixture.paySignInfoMapper.selectSignStatusBySeq(SEQ)).thenReturn("UNSIGNED");

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), METRO_APP);

        assertEquals("UNSIGNED", result.getStatus());
    }

    private void verifyNoStatusMigration(PaySignFacadeFixture fixture) {
        verify(fixture.paySignInfoMapper, never()).markSigned(anyString(), anyString(), anyString(), any());
        verify(fixture.paySignInfoMapper, never()).markUnsigned(anyString(), any());
        verify(fixture.paySignInfoMapper, never()).reactivateForResign(anyString());
    }

    private RequestContractResultReqDTO query() {
        RequestContractResultReqDTO request = new RequestContractResultReqDTO();
        request.setThirdUserId(USER);
        request.setRequestSignSeq(SEQ);
        request.setPaymentVendor(ALIPAY_VENDOR);
        return request;
    }

    /** 本地记录一律缺 payAccountId，好让 needCallGateway 成立、走到出网那一支。 */
    private PaySignInfo localRecord(String contractStatus) {
        PaySignInfo signInfo = new PaySignInfo();
        signInfo.setRequestSignSeq(SEQ);
        signInfo.setThirdUserId(USER);
        signInfo.setPaymentVendor(ALIPAY_VENDOR);
        signInfo.setContractStatus(contractStatus);
        return signInfo;
    }

    private PaySignGatewayResponse gatewayData(String status) {
        PaySignGatewayResponse response = PaySignFacadeFixture.gatewaySuccess();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        data.put("payUserId", "PA-9");
        data.put("payAgreementNo", "AGR-9");
        response.setData(data);
        return response;
    }
}
