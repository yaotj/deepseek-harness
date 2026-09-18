package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.RequestAgreeReleaseReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import org.junit.jupiter.api.Test;

/**
 * 护栏：removeSignAgreement（「移除签约」）的五条口径。
 *
 * <p>本方法此前零行为测试 —— 只有 PaySignTransactionBoundaryArchTest 按方法名做过事务边界静态检查，
 * 即「它带不带 @Transactional」有人管、「它答什么 / 写不写库」没人管。这个组合危险在于它
 * 直接把签约主表改成 UNSIGNED：判空、存在性、状态机三道里任一道回退，表现都是
 * 「接口返 0000 而库里状态没动」或反过来「状态被改掉却对上游报错」，两者都不会抛异常。
 *
 * <p>五条各钉一侧，NEVER 只留 happy path。
 */
class ContractRemoveAgreementGuardTest {

    private static final String SEQ = "0052290701523999";
    private static final String USER = "U-TEST-0001";
    private static final String ALIPAY_VENDOR = "03";
    private static final String METRO_APP = "01";

    /** 口径 1：agreementCode 为空 ⇒ 8001 + 语义码 400，且一次都不查库。 */
    @Test
    void blankAgreementCodeIsRejectedBeforeTouchingDatabase() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

        RequestAgreeReleaseResult result = fixture.service.removeSignAgreement(request("  "), METRO_APP);

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        assertEquals(400, result.getCode());
        assertEquals(Boolean.FALSE, result.getSuccess());
        verify(fixture.paySignInfoMapper, never()).selectBySeq(anyString(), any());
        verify(fixture.paySignInfoMapper, never()).markUnsigned(anyString(), any());
    }

    /** 口径 2：签约记录不存在 ⇒ 8012 + 404，NEVER 发起 CAS（否则等于 UPDATE 一个不存在的协议号）。 */
    @Test
    void missingSignRecordIsRejectedWithoutCas() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(null);

        RequestAgreeReleaseResult result = fixture.service.removeSignAgreement(request(SEQ), METRO_APP);

        assertEquals(PaySignErrorCodeEnum.RECORD_NOT_EXIST.getCode(), result.getRetCode());
        assertEquals(404, result.getCode());
        assertEquals(Boolean.FALSE, result.getSuccess());
        verify(fixture.paySignInfoMapper, never()).markUnsigned(anyString(), any());
    }

    /** 口径 3：CAS 命中 ⇒ 0000 + code 0 + success=true，且确实调了 markUnsigned。 */
    @Test
    void successfulCasAnswersSuccessAndPersistsUnsigned() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(signedRecord());
        when(fixture.paySignInfoMapper.markUnsigned(anyString(), any())).thenReturn(1);

        RequestAgreeReleaseResult result = fixture.service.removeSignAgreement(request(SEQ), METRO_APP);

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals(0, result.getCode());
        assertEquals(Boolean.TRUE, result.getSuccess());
        verify(fixture.paySignInfoMapper).markUnsigned(anyString(), any());
    }

    /**
     * 口径 4：CAS 落空但库里已是 UNSIGNED ⇒ 幂等重放，仍答成功。
     *
     * <p>这是给上游重推留的出口：解约天然会被重复调用，把「已经解过」判成失败会让上游一直重推，
     * 运维那边则看到一堆假失败。
     */
    @Test
    void casMissOnAlreadyUnsignedIsIdempotentSuccess() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(signedRecord());
        when(fixture.paySignInfoMapper.markUnsigned(anyString(), any())).thenReturn(0);
        when(fixture.paySignInfoMapper.selectSignStatusBySeq(SEQ)).thenReturn("UNSIGNED");

        RequestAgreeReleaseResult result = fixture.service.removeSignAgreement(request(SEQ), METRO_APP);

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals(Boolean.TRUE, result.getSuccess());
    }

    /**
     * 口径 5：CAS 落空且库里不是目标态 ⇒ 8001 + 409，文案带上实测到的当前状态。
     *
     * <p><b>NEVER 把这一支也答成成功</b>：CAS 影响 0 行说明白名单不允许这条迁移（或已被并发改走），
     * 此时签约主表仍是可用状态 —— 答成功等于告诉上游「已解约」，而用户下次过闸照样被扣款。
     */
    @Test
    void casMissWithForeignStatusIsRejectedAsConflict() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(signedRecord());
        when(fixture.paySignInfoMapper.markUnsigned(anyString(), any())).thenReturn(0);
        when(fixture.paySignInfoMapper.selectSignStatusBySeq(SEQ)).thenReturn("SIGN_FAILED");

        RequestAgreeReleaseResult result = fixture.service.removeSignAgreement(request(SEQ), METRO_APP);

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        assertEquals(409, result.getCode());
        assertEquals(Boolean.FALSE, result.getSuccess());
        assertTrue(result.getRetMsg().contains("SIGN_FAILED"),
                "拒绝文案 MUST 带上实测到的当前状态，否则运维只能猜是哪一态挡的");
    }

    private RequestAgreeReleaseReqDTO request(String agreementCode) {
        RequestAgreeReleaseReqDTO request = new RequestAgreeReleaseReqDTO();
        request.setAgreementCode(agreementCode);
        return request;
    }

    private PaySignInfo signedRecord() {
        PaySignInfo signInfo = new PaySignInfo();
        signInfo.setRequestSignSeq(SEQ);
        signInfo.setThirdUserId(USER);
        signInfo.setPaymentVendor(ALIPAY_VENDOR);
        signInfo.setContractStatus("SIGNED");
        return signInfo;
    }
}
