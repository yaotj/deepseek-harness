package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * IF8A-22 {@code requestContractResult} **非钱包主干**的特征测试（2026-09-16 新增，ADR-D107 前置）。
 *
 * <p><b>为什么必须先有本类</b>：这是 `ContractDomainServiceImpl` 里最复杂的方法（109 行、4 个协作者），
 * 而它的非钱包主干此前是**零覆盖**的 —— 全仓 grep `requestContractResult`，测试侧只有
 * {@code AccountReadCharacterizationTest} 那 4 条，而它们传的是 {@code walletQuery()}，
 * 在方法第 3 行的钱包分支就 {@code return} 了，**从未进入主干**。
 * 因此在补上本类之前，任何对该方法的重构都是「无护栏改控制流」。
 *
 * <p><b>断言挂在 {@code PaySignService} 门面上</b>（经 {@link PaySignFacadeFixture}），
 * 与 D95 / D98 同一条理由：代码在底下怎么重排，「绿」都仍然证明对外行为等价。
 *
 * <p>钉住的六条口径，<b>NEVER 为了让某次重构通过而放宽</b>：
 * <ol>
 *   <li>本地已签约且数据完整 ⇒ 直接返回、**绝不出网**（省一次支付中心往返）；</li>
 *   <li>流水号命中的记录不属于报文里的 {@code thirdUserId} ⇒ 一律按「签约记录不存在」回绝，
 *       且**不出网** —— 这条是防「凭流水号探测他人协议」的归属校验；</li>
 *   <li>网关业务失败 ⇒ {@code SYSTEM_ERROR}，不改本地状态；</li>
 *   <li>本地有记录 + 平台返 SIGNED ⇒ 落库走 {@code markSigned}；</li>
 *   <li>本地无记录 + 平台返 SIGNED（**孤儿协议**）⇒ 照实返回但 <b>NEVER insert</b>；</li>
 *   <li>网关 {@code data} 为空 + 本地无记录 ⇒ 归一成 {@code NOT_SIGNED}。</li>
 * </ol>
 */
class ContractResultCharacterizationTest {

    private static final String ALIPAY_VENDOR = "03";
    private static final String SEQ = "0052290701523999";
    private static final String USER = "U-TEST-0001";

    /** 口径 1：本地已签约且 payAccountId / payAgreementNo 齐全，直接返回、不出网。 */
    @Test
    void localSignedRecordShortCircuitsWithoutGateway() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(signedLocalRecord());

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("SIGNED", result.getStatus());
        assertEquals("PA-1", result.getPayAccountId());
        assertEquals("PA-1", result.getPayUserId());
        assertEquals("AGR-1", result.getPayAgreementNo());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    /** 口径 2：归属校验失败 ⇒ 按「记录不存在」回绝，且不出网、不泄露真实归属。 */
    @Test
    void ownershipMismatchIsAnsweredAsRecordNotExistAndNeverReachesGateway() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignInfo other = signedLocalRecord();
        other.setThirdUserId("U-SOMEONE-ELSE");
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(other);

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), "01");

        assertEquals(PaySignErrorCodeEnum.RECORD_NOT_EXIST.getCode(), result.getRetCode());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    /** 口径 3：网关业务失败 ⇒ SYSTEM_ERROR，且不落库。 */
    @Test
    void gatewayBusinessFailureMapsToSystemErrorWithoutPersisting() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(null);
        fixture.gatewayReturns(PaySignFacadeFixture.gatewayFailure(600, "操作失败"));

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper, never()).insert(any());
        verify(fixture.paySignInfoMapper, never()).markSigned(anyString(), anyString(), anyString(), any());
    }

    /** 口径 4：本地有记录 + 平台返 SIGNED ⇒ 走 markSigned 落库。 */
    @Test
    void existingRecordRefreshedToSignedGoesThroughMarkSigned() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignInfo local = signedLocalRecord();
        local.setContractStatus("NOT_SIGNED");
        local.setPayAccountId(null);
        local.setPayAgreementNo(null);
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(local);
        fixture.gatewayReturns(gatewaySignedData());
        when(fixture.paySignInfoMapper.markSigned(anyString(), anyString(), anyString(), any())).thenReturn(1);

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("SIGNED", result.getStatus());
        verify(fixture.paySignInfoMapper).markSigned(anyString(), anyString(), anyString(), any());
    }

    /**
     * 口径 5：**孤儿协议** —— 平台说已签约、本地没有签约记录。
     *
     * <p>照实返回给 APP，但 <b>NEVER insert</b>：本接口是查询接口，报文里拿不到
     * {@code CARD_ID} / {@code CARD_TYPE}，凭空造记录会让解约链路拿 {@code NO_ACCOUNT_CARD} 卡死在
     * SCANNING（生产已发生 3 条，只能改库清理）。<b>NEVER 因为「补一条更完整」而放开落库。</b>
     */
    @Test
    void orphanSignedContractIsReturnedButNeverPersisted() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(null);
        fixture.gatewayReturns(gatewaySignedData());

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("SIGNED", result.getStatus());
        assertEquals("PA-9", result.getPayAccountId());
        verify(fixture.paySignInfoMapper, never()).insert(any());
        verify(fixture.paySignInfoMapper, never()).markSigned(anyString(), anyString(), anyString(), any());
    }

    /** 口径 6：网关 data 为空 + 本地无记录 ⇒ 归一成 NOT_SIGNED，仍返 0000。 */
    @Test
    void emptyGatewayDataWithoutLocalRecordFallsBackToNotSigned() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(null);
        fixture.gatewayReturns(PaySignFacadeFixture.gatewaySuccess());

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("NOT_SIGNED", result.getStatus());
        verify(fixture.paySignInfoMapper, never()).insert(any());
    }

    private RequestContractResultReqDTO query() {
        RequestContractResultReqDTO request = new RequestContractResultReqDTO();
        request.setThirdUserId(USER);
        request.setRequestSignSeq(SEQ);
        request.setPaymentVendor(ALIPAY_VENDOR);
        return request;
    }

    private PaySignInfo signedLocalRecord() {
        PaySignInfo signInfo = new PaySignInfo();
        signInfo.setRequestSignSeq(SEQ);
        signInfo.setThirdUserId(USER);
        signInfo.setPaymentVendor(ALIPAY_VENDOR);
        signInfo.setContractStatus("SIGNED");
        signInfo.setPayAccountId("PA-1");
        signInfo.setPayAgreementNo("AGR-1");
        return signInfo;
    }

    private PaySignGatewayResponse gatewaySignedData() {
        PaySignGatewayResponse response = PaySignFacadeFixture.gatewaySuccess();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "SIGNED");
        data.put("payUserId", "PA-9");
        data.put("payAgreementNo", "AGR-9");
        response.setData(data);
        return response;
    }
}
