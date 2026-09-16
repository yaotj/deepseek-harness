package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.UpdateHceDataReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * IF1A-01 卡种与签约信息富化器特征测试。
 *
 * <p>钉住的是**两个来源、四组同形字段**的落值口径。这四组字段各自都有过事故：</p>
 * <ul>
 *   <li>{@code requestSignSeq} MUST 同时写 request 与 response —— 只写 request 时
 *       fep-dev-server 拿不到，下游 gate-txn-pay 的免密扣款缺签约协议（2026-08-26 修复）；</li>
 *   <li>{@code paymentVendor} 为空会让反向推码改用闸机上送的 signChannelCode（那是票种语义），
 *       支付宝码体渠道位出错且刷过离线码后不自愈（2026-09-11 修复）；</li>
 *   <li>支付宝侧 {@code cardType} 是 APP 口径 2 位码，MUST 过 CardTypeMapping 转 4 位发卡票种，
 *       否则下游判不中员工票 / 日票，免扣费与日票扣次整段失效；</li>
 *   <li>员工票 / 日票的金额清零 MUST 落在 request 上（明细与行业推送都从 request 取金额）。</li>
 * </ul>
 * <p>另钉住一条容易被"顺手统一"掉的语义差异：account 域**返回未命中**才回落支付宝，
 * account 域**抛异常**直接吞掉、<b>不回落</b>。</p>
 */
class GateCardTypeEnricherTest {

    private AccountClient accountClient;
    private AlipayAccountClient alipayAccountClient;
    private GateCardTypeEnricher enricher;

    @BeforeEach
    void setUp() {
        accountClient = mock(AccountClient.class);
        alipayAccountClient = mock(AlipayAccountClient.class);
        enricher = new GateCardTypeEnricher();
        ReflectionTestUtils.setField(enricher, "accountClient", accountClient);
        ReflectionTestUtils.setField(enricher, "alipayAccountClient", alipayAccountClient);
    }

    /** 闸机上送的原始报文：卡种是二维码行业卡类型 0441，金额非零。 */
    private NotifyVerifyResultReqDTO gateRequest() {
        NotifyVerifyResultReqDTO request = new NotifyVerifyResultReqDTO();
        request.setCardId("0426091000000013");
        request.setCardType("0441");
        request.setTrxAmount("2");
        request.setOvertimeAmount("1");
        return request;
    }

    private QueryUserInfoResult accountHit(String cardType) {
        QueryUserInfoResult result = new QueryUserInfoResult();
        result.setRetCode("0000");
        result.setCardType(cardType);
        result.setCompanionFlag("Y");
        result.setChannel("01");
        result.setReqContractNo("SIGN20260914001");
        result.setThirdPayId("PAY20260914001");
        return result;
    }

    private AlipayUserInfoDTO alipayHit(String appCardType) {
        AlipayUserInfoDTO user = new AlipayUserInfoDTO();
        user.setCardType(appCardType);
        user.setChannel("07");
        user.setReqContractNo("ALI20260914001");
        user.setThirdPayId("ALIPAY20260914001");
        user.setThirdUserId("2088ALIPAYUSER");
        return user;
    }

    @Test
    @DisplayName("account 域命中：四组字段各就各位，不回落支付宝")
    void account域命中时四组字段各就各位() {
        when(accountClient.queryCardTypeByCardId("0426091000000013")).thenReturn(accountHit("044A"));
        NotifyVerifyResultReqDTO request = gateRequest();
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();

        enricher.applyActualCardType(request, response);

        assertEquals("044A", request.getCardType(), "以开户记录为准，鲁通码 MUST 从 0441 恢复成 044A");
        assertEquals("Y", request.getCompanionFlag());
        assertEquals("01", request.getPaymentVendor(), "来自 USER_ITP_REG_INFO.CHANNEL");
        assertEquals("SIGN20260914001", request.getRequestSignSeq());
        assertEquals("SIGN20260914001", response.getRequestSignSeq(),
                "MUST 同时写 response，否则 fep-dev-server 拿不到、下游免密扣款缺签约协议");
        assertEquals("PAY20260914001", response.getPayUserId(), "钱包支付 0B 扣款用");
        verifyNoInteractions(alipayAccountClient);
    }

    @Test
    @DisplayName("account 域命中但字段带空格：一律 trim 后落值")
    void account域字段一律trim后落值() {
        QueryUserInfoResult hit = accountHit("  0442  ");
        hit.setCompanionFlag(" C ");
        hit.setChannel(" 0B ");
        hit.setReqContractNo(" SIGN001 ");
        hit.setThirdPayId(" PAY001 ");
        when(accountClient.queryCardTypeByCardId(anyString())).thenReturn(hit);
        NotifyVerifyResultReqDTO request = gateRequest();
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();

        enricher.applyActualCardType(request, response);

        assertEquals("0442", request.getCardType());
        assertEquals("C", request.getCompanionFlag());
        assertEquals("0B", request.getPaymentVendor());
        assertEquals("SIGN001", request.getRequestSignSeq());
        assertEquals("SIGN001", response.getRequestSignSeq());
        assertEquals("PAY001", response.getPayUserId());
    }

    @Test
    @DisplayName("account 域命中但可选字段为空：保留原值、不写 null")
    void account域可选字段为空时保留原值() {
        QueryUserInfoResult hit = accountHit("0441");
        hit.setCompanionFlag(null);
        hit.setChannel("  ");
        hit.setReqContractNo(null);
        hit.setThirdPayId(null);
        when(accountClient.queryCardTypeByCardId(anyString())).thenReturn(hit);
        NotifyVerifyResultReqDTO request = gateRequest();
        request.setCompanionFlag("N");
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();

        enricher.applyActualCardType(request, response);

        assertEquals("N", request.getCompanionFlag(), "空值 MUST NOT 覆盖闸机上送值");
        assertNull(request.getPaymentVendor(), "空白渠道不写，只打 WARN");
        assertNull(response.getRequestSignSeq());
        assertNull(response.getPayUserId());
    }

    @Test
    @DisplayName("account 域三种未命中形态都回落支付宝账户域")
    void account域三种未命中形态都回落支付宝() {
        QueryUserInfoResult blankCardType = accountHit("  ");
        QueryUserInfoResult rejected = accountHit("0441");
        rejected.setRetCode("8004");
        for (QueryUserInfoResult miss : new QueryUserInfoResult[]{null, rejected, blankCardType}) {
            when(accountClient.queryCardTypeByCardId(anyString())).thenReturn(miss);
            when(alipayAccountClient.selectByCardId(anyString())).thenReturn(alipayHit("02"));
            NotifyVerifyResultReqDTO request = gateRequest();
            NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();

            enricher.applyActualCardType(request, response);

            assertEquals("07", request.getPaymentVendor(), "MUST 已按支付宝账户域补齐");
            assertEquals("ALI20260914001", response.getRequestSignSeq());
        }
    }

    @Test
    @DisplayName("支付宝侧卡种 MUST 过 CardTypeMapping 转 4 位发卡票种")
    void 支付宝侧卡种MUST转4位发卡票种() {
        when(accountClient.queryCardTypeByCardId(anyString())).thenReturn(null);
        when(alipayAccountClient.selectByCardId(anyString())).thenReturn(alipayHit("02"));
        NotifyVerifyResultReqDTO request = gateRequest();

        enricher.applyActualCardType(request, new NotifyVerifyResultRespDTO());

        assertEquals("0441", request.getCardType(),
                "APP 口径 02 MUST 转成 0441，NEVER 把 2 位码直接塞进去——下游按 4 位判员工票/日票");
    }

    @Test
    @DisplayName("支付宝侧命中：五组字段落值，itpUserId 也要回填")
    void 支付宝侧命中时五组字段落值() {
        when(accountClient.queryCardTypeByCardId(anyString())).thenReturn(null);
        when(alipayAccountClient.selectByCardId(anyString())).thenReturn(alipayHit("02"));
        NotifyVerifyResultReqDTO request = gateRequest();
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();

        enricher.applyActualCardType(request, response);

        assertEquals("07", request.getPaymentVendor(), "缺它则反向推码改用闸机的 signChannelCode（票种语义）、码体渠道位出错");
        assertEquals("ALI20260914001", request.getRequestSignSeq());
        assertEquals("ALI20260914001", response.getRequestSignSeq());
        assertEquals("ALIPAY20260914001", response.getPayUserId());
        assertEquals("2088ALIPAYUSER", request.getItpUserId(), "支付宝链路独有：itpUserId 由 thirdUserId 回填");
    }

    @Test
    @DisplayName("支付宝也未命中：保留闸机上送值，一个字段都不动")
    void 支付宝也未命中时保留闸机上送值() {
        when(accountClient.queryCardTypeByCardId(anyString())).thenReturn(null);
        when(alipayAccountClient.selectByCardId(anyString())).thenReturn(null);
        NotifyVerifyResultReqDTO request = gateRequest();
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();

        enricher.applyActualCardType(request, response);

        assertEquals("0441", request.getCardType());
        assertNull(request.getPaymentVendor());
        assertNull(response.getRequestSignSeq());
        assertEquals("2", request.getTrxAmount(), "非免费卡种 MUST NOT 清零金额");
    }

    @Test
    @DisplayName("免扣费清零：员工票与日票族两条链路都要生效")
    void 免扣费清零两条链路都要生效() {
        for (String freeCardType : new String[]{"0444", "0445", "0446", "0447", "0448"}) {
            when(accountClient.queryCardTypeByCardId(anyString())).thenReturn(accountHit(freeCardType));
            NotifyVerifyResultReqDTO viaAccount = gateRequest();
            enricher.applyActualCardType(viaAccount, new NotifyVerifyResultRespDTO());
            assertEquals("0", viaAccount.getTrxAmount(), freeCardType + " account 链路 MUST 清零");
            assertEquals("0", viaAccount.getOvertimeAmount(), freeCardType + " 超时费也 MUST 清零");
        }
        // 支付宝链路：APP 口径 11 → 0444 员工票
        when(accountClient.queryCardTypeByCardId(anyString())).thenReturn(null);
        when(alipayAccountClient.selectByCardId(anyString())).thenReturn(alipayHit("11"));
        NotifyVerifyResultReqDTO viaAlipay = gateRequest();
        enricher.applyActualCardType(viaAlipay, new NotifyVerifyResultRespDTO());
        assertEquals("0444", viaAlipay.getCardType());
        assertEquals("0", viaAlipay.getTrxAmount(), "支付宝链路 MUST 同样清零，NEVER 只在一条链路上改");
        assertEquals("0", viaAlipay.getOvertimeAmount());
    }

    @Test
    @DisplayName("支付宝卡种映射为空时按闸机上送卡种判免扣费")
    void 支付宝卡种映射为空时按闸机上送卡种判免扣费() {
        when(accountClient.queryCardTypeByCardId(anyString())).thenReturn(null);
        when(alipayAccountClient.selectByCardId(anyString())).thenReturn(alipayHit(null));
        NotifyVerifyResultReqDTO request = gateRequest();
        request.setCardType("0444");

        enricher.applyActualCardType(request, new NotifyVerifyResultRespDTO());

        assertEquals("0444", request.getCardType(), "映射空则保留闸机上送卡种");
        assertEquals("0", request.getTrxAmount(), "免扣费判定用的是保留下来的那个卡种");
    }

    @Test
    @DisplayName("account 域抛异常时直接吞掉、NEVER 回落支付宝（与「返回未命中」口径不同）")
    void account域抛异常时不回落支付宝() {
        when(accountClient.queryCardTypeByCardId(anyString()))
                .thenThrow(new RuntimeException("connect timed out"));
        NotifyVerifyResultReqDTO request = gateRequest();
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();

        enricher.applyActualCardType(request, response);

        assertEquals("0441", request.getCardType(), "保留闸机上送值、不抛异常、不影响过闸主流程");
        assertNull(response.getRequestSignSeq());
        verifyNoInteractions(alipayAccountClient);
    }

    @Test
    @DisplayName("支付宝侧抛异常同样被最外层吞掉，不影响过闸")
    void 支付宝侧抛异常同样被吞掉() {
        when(accountClient.queryCardTypeByCardId(anyString())).thenReturn(null);
        when(alipayAccountClient.selectByCardId(anyString()))
                .thenThrow(new RuntimeException("connect timed out"));
        NotifyVerifyResultReqDTO request = gateRequest();

        enricher.applyActualCardType(request, new NotifyVerifyResultRespDTO());

        assertEquals("0441", request.getCardType());
    }

    @Test
    @DisplayName("HCE 回写只在「HCE 卡种 + reserve1 有值」时发起")
    void HCE回写的触发条件() {
        NotifyVerifyResultReqDTO notHce = gateRequest();
        notHce.setReserve1("AABB");
        enricher.updateHceDataFromGateTransaction(notHce);

        NotifyVerifyResultReqDTO noReserve = gateRequest();
        noReserve.setCardType("0442");
        enricher.updateHceDataFromGateTransaction(noReserve);

        verify(accountClient, never()).updateHceData(any());
    }

    @Test
    @DisplayName("HCE 回写：卡数据 trim 后上送，0442 与 0443 都算 HCE")
    void HCE回写卡数据trim后上送() {
        ArgumentCaptor<UpdateHceDataReqDTO> captor = ArgumentCaptor.forClass(UpdateHceDataReqDTO.class);
        for (String hceCardType : new String[]{"0442", "0443"}) {
            NotifyVerifyResultReqDTO request = gateRequest();
            request.setCardType(hceCardType);
            request.setReserve1("  A1B2C3  ");
            enricher.updateHceDataFromGateTransaction(request);
        }
        verify(accountClient, times(2)).updateHceData(captor.capture());
        assertEquals("A1B2C3", captor.getValue().getHceData());
        assertEquals("0426091000000013", captor.getValue().getCardId());
    }

    @Test
    @DisplayName("HCE 回写失败只记日志：交易已完成，闸机重试可再次触发")
    void HCE回写失败只记日志() {
        when(accountClient.updateHceData(any())).thenThrow(new RuntimeException("connect timed out"));
        NotifyVerifyResultReqDTO request = gateRequest();
        request.setCardType("0442");
        request.setReserve1("A1B2C3");

        enricher.updateHceDataFromGateTransaction(request);
    }
}
