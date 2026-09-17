package com.chinasofti.huateng.industry.service.impl;

import com.chinasofti.huateng.model.app.IndustryCardDataBuildReqDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;
import com.chinasofti.huateng.model.app.RequestSignInsDataRespDTO;
import com.chinasofti.huateng.rpc.security.SecurityClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 行业卡码体特征测试（characterization test）。 */
class IndustryCardDataBodyTest {

    private SecurityClient securityClient;
    private IndustryCardDataServiceImpl service;

    @BeforeEach
    void setUp() {
        securityClient = mock(SecurityClient.class);
        service = new IndustryCardDataServiceImpl(securityClient);
        ReflectionTestUtils.setField(service, "defaultIssueChannelCode", "01");
        ReflectionTestUtils.setField(service, "defaultTicketType", "0441");
        ReflectionTestUtils.setField(service, "timestampExpireHours", 4);
    }

    /** 基准请求：所有段都给确定值，单个用例只改自己关心的那一个字段。 */
    private IndustryCardDataBuildReqDTO baseRequest() {
        IndustryCardDataBuildReqDTO request = new IndustryCardDataBuildReqDTO();
        request.setThirdUserId("12345");
        request.setCardId("0426091000000013");
        request.setCardType("0441");
        request.setTicketStatus("02");
        request.setLastTxnStation("1234");
        request.setLastTxnTime("20260914120000");
        request.setTxnSeq("7");
        request.setIssueChannelCode("07");
        request.setSignChannelCode("01");
        return request;
    }

    private String bodyOf(IndustryCardDataBuildReqDTO request) {
        RequestSignInsDataRespDTO signResp = new RequestSignInsDataRespDTO();
        signResp.setRetCode("0000");
        signResp.setIndustryDataSign("A1B2C3D4E5F60718");
        when(securityClient.requestSignInsData(any())).thenReturn(signResp);
        IndustryCardDataBuildRespDTO response = service.buildCardData(request);
        return response.getUnsignedIndustryData();
    }

    private String seg(String body, int offset, int length) {
        return body.substring(offset, offset + length);
    }

    @Test
    @DisplayName("码体恒为 64 位大写十六进制")
    void 码体恒为64位大写十六进制() {
        String body = bodyOf(baseRequest());
        assertEquals(64, body.length(), "定长 64 位，MUST NOT 变");
        assertTrue(body.matches("[0-9A-F]{64}"), "只允许 0-9A-F，出现小写或非 hex 即下游签名报错");
    }

    @Test
    @DisplayName("基准入参下 11 段逐段取值")
    void 基准入参下11段逐段取值() {
        String body = bodyOf(baseRequest());
        assertEquals("00003039", seg(body, 0, 8), "thirdUserId=12345 → 0x3039");
        assertEquals("02", seg(body, 8, 2), "ticketStatus 原样 2 位");
        assertEquals("1234", seg(body, 10, 4), "lastTxnStation 优先");
        assertEquals("323A9E40", seg(body, 14, 8), "handleDate = 20260914120000 距 2000-01-01 的秒数");
        assertTrue(seg(body, 22, 8).matches("[0-9A-F]{8}"), "timeStamp 取 now()+4h，只能断言形态");
        assertEquals("0426091000000013", seg(body, 30, 16), "ticketLogicNo 即 cardId");
        assertEquals("0441", seg(body, 46, 4), "ticketType");
        assertEquals("00000007", seg(body, 50, 8), "transSeq=7");
        assertEquals("07", seg(body, 58, 2), "issueChannelCode");
        assertEquals("01", seg(body, 60, 2), "signChannelCode");
        assertEquals("01", seg(body, 62, 2), "末 2 位是固定 01");
    }

    @Test
    @DisplayName("thirdUserId 非数字时回落 hashCode，不抛异常")
    void thirdUserId非数字时回落hashCode() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setThirdUserId("abc");
        assertEquals("00017862", seg(bodyOf(request), 0, 8), "\"abc\".hashCode()=96354=0x17862");
    }

    @Test
    @DisplayName("ticketStatus 缺省为 03（新卡）")
    void ticketStatus缺省为03() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setTicketStatus(null);
        assertEquals("03", seg(bodyOf(request), 8, 2));
    }

    @Test
    @DisplayName("车站段：lastTxnStation 全零视为无值，回落 gateInStation")
    void 车站段全零回落进站站点() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setLastTxnStation("0000");
        request.setGateInStation("5678");
        assertEquals("5678", seg(bodyOf(request), 10, 4), "全零 MUST 当无值，NEVER 直接填 0000");
    }

    @Test
    @DisplayName("车站段：两个来源都无值时填 FFFF")
    void 车站段两处都无值时填FFFF() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setLastTxnStation(null);
        request.setGateInStation("0000");
        assertEquals("FFFF", seg(bodyOf(request), 10, 4));
    }

    @Test
    @DisplayName("车站段：不足 4 位左补零")
    void 车站段不足4位左补零() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setLastTxnStation("12");
        assertEquals("0012", seg(bodyOf(request), 10, 4));
    }

    @Test
    @DisplayName("时间段：lastTxnTime 无值时回落 gateInTime")
    void 时间段无值时回落进站时间() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setLastTxnTime(null);
        request.setGateInTime("20260914120000");
        assertEquals("323A9E40", seg(bodyOf(request), 14, 8));
    }

    @Test
    @DisplayName("时间段：长度非 14 或全零时静默改用当前时间")
    void 时间段格式非法时静默改用当前时间() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setLastTxnTime("2026091412");
        String withBadLength = seg(bodyOf(request), 14, 8);
        request.setLastTxnTime("00000000000000");
        String withAllZeros = seg(bodyOf(request), 14, 8);
        assertTrue(withBadLength.matches("[0-9A-F]{8}"), "非法格式不抛异常、不留空段");
        assertTrue(withAllZeros.matches("[0-9A-F]{8}"));
        assertEquals(false, "323A9E40".equals(withBadLength), "MUST NOT 还是那个业务时间");
    }

    @Test
    @DisplayName("票种段：员工票与日票族统一压成 0441")
    void 票种段员工票与日票族统一压成0441() {
        for (String cardType : new String[]{"0444", "0445", "0446", "0447", "0448", "044A"}) {
            IndustryCardDataBuildReqDTO request = baseRequest();
            request.setCardType(cardType);
            assertEquals("0441", seg(bodyOf(request), 46, 4),
                    cardType + " 的账户卡种不是 0441，但行业码体 MUST 用 0441");
        }
    }

    @Test
    @DisplayName("票种段：HCE 保留自己的卡种")
    void 票种段HCE保留自己的卡种() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setCardType("0442");
        assertEquals("0442", seg(bodyOf(request), 46, 4));
        request.setCardType("0443");
        assertEquals("0443", seg(bodyOf(request), 46, 4));
    }

    @Test
    @DisplayName("票种段：APP 两位码先经 CardTypeMapping 展开再判断")
    void 票种段APP两位码先展开再判断() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setCardType("12");
        assertEquals("0441", seg(bodyOf(request), 46, 4), "12 → 0445（一日票）→ 压成 0441");
        request.setCardType("03");
        assertEquals("0442", seg(bodyOf(request), 46, 4), "03 → 0442，HCE 不压");
    }

    @Test
    @DisplayName("卡号段：超 16 位取右 16 位，不足则左补零")
    void 卡号段超长取右16位不足左补零() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setCardId("990426091000000013");
        assertEquals("0426091000000013", seg(bodyOf(request), 30, 16), "取右侧 16 位");
        request.setCardId("13");
        assertEquals("0000000000000013", seg(bodyOf(request), 30, 16));
    }

    @Test
    @DisplayName("离线码的 signChannelCode 固定 17，原样进码体")
    void 离线码签名渠道17原样进码体() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setSignChannelCode("17");
        assertEquals("17", seg(bodyOf(request), 60, 2));
    }

    @Test
    @DisplayName("渠道段：4 位发卡机构码被截成右 2 位（已知有损，仅留 WARN）")
    void 渠道段超长截成右2位() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setIssueChannelCode("5412");
        assertEquals("12", seg(bodyOf(request), 58, 2), "截断是现行行为，改动 MUST 先与 ACC 对齐");
        request.setIssueChannelCode("0007");
        assertEquals("07", seg(bodyOf(request), 58, 2), "account 侧上送的 0007 截出的是合法值");
    }

    @Test
    @DisplayName("渠道段：两个渠道位缺省都是 01")
    void 渠道段缺省都是01() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setIssueChannelCode(null);
        request.setSignChannelCode(null);
        String body = bodyOf(request);
        assertEquals("01", seg(body, 58, 2));
        assertEquals("01", seg(body, 60, 2));
    }

    @Test
    @DisplayName("入参校验三个必填项各自返回 8001，且不调签名服务")
    void 入参校验三个必填项各自返回8001() {
        assertEquals("8001", service.buildCardData(null).getRetCode());
        for (String missing : new String[]{"thirdUserId", "cardId", "cardType"}) {
            IndustryCardDataBuildReqDTO request = baseRequest();
            switch (missing) {
                case "thirdUserId" -> request.setThirdUserId("  ");
                case "cardId" -> request.setCardId(null);
                default -> request.setCardType(null);
            }
            IndustryCardDataBuildRespDTO response = service.buildCardData(request);
            assertEquals("8001", response.getRetCode(), missing + " 缺失 MUST 返 8001");
            assertTrue(response.getRetMsg().contains(missing), "错误信息 MUST 点名字段：" + response.getRetMsg());
        }
    }

    @Test
    @DisplayName("码体含非 hex 字符时本服务自己拦下，不送签名")
    void 码体含非hex字符时本服务自己拦下() {
        IndustryCardDataBuildReqDTO request = baseRequest();
        request.setTicketStatus("ZZ");
        IndustryCardDataBuildRespDTO response = service.buildCardData(request);
        assertEquals("8001", response.getRetCode(),
                "MUST 在本服务拦下，NEVER 让 acc-security-server 报误导性的 hexString length odd");
        assertTrue(response.getRetMsg().contains("十六进制"));
    }

    @Test
    @DisplayName("成功时 cardData = 64 位码体 + 16 位签名")
    void 成功时cardData等于码体加16位签名() {
        RequestSignInsDataRespDTO signResp = new RequestSignInsDataRespDTO();
        signResp.setRetCode("0000");
        signResp.setIndustryDataSign("a1b2c3d4e5f60718");
        when(securityClient.requestSignInsData(any())).thenReturn(signResp);

        IndustryCardDataBuildRespDTO response = service.buildCardData(baseRequest());
        assertEquals("0000", response.getRetCode());
        assertEquals(80, response.getCardData().length(), "64 + 16，长度变了闸机必然验不过");
        assertTrue(response.getCardData().startsWith(response.getUnsignedIndustryData()));
        assertEquals("A1B2C3D4E5F60718", response.getCardData().substring(64), "签名段同样归一成大写");
    }

    @Test
    @DisplayName("签名服务 retCode 200 也算成功（历史兼容口径）")
    void 签名服务retCode200也算成功() {
        RequestSignInsDataRespDTO signResp = new RequestSignInsDataRespDTO();
        signResp.setRetCode("200");
        signResp.setIndustryDataSign("A1B2C3D4E5F60718");
        when(securityClient.requestSignInsData(any())).thenReturn(signResp);
        assertEquals("0000", service.buildCardData(baseRequest()).getRetCode(),
                "0000 与 200 两个口径都在用，NEVER 只留一个");
    }

    @Test
    @DisplayName("签名服务无应答返 9999，业务失败原样透传")
    void 签名服务失败时的两种返回() {
        when(securityClient.requestSignInsData(any())).thenReturn(null);
        assertEquals("9999", service.buildCardData(baseRequest()).getRetCode());

        RequestSignInsDataRespDTO rejected = new RequestSignInsDataRespDTO();
        rejected.setRetCode("8003");
        rejected.setRetMsg("加密机无可用连接");
        when(securityClient.requestSignInsData(any())).thenReturn(rejected);
        IndustryCardDataBuildRespDTO response = service.buildCardData(baseRequest());
        assertEquals("8003", response.getRetCode(), "MUST 透传下游错误码");
        assertEquals("加密机无可用连接", response.getRetMsg());
    }

    /** 已知地雷：签名段为空时走的是「签名失败」分支，而该分支把下游的 {@code retCode} 原样回填， 于是 {@code retCode=0000} 与 {@code cardData=null} 同时出现——上游按 retCode 判成功就会拿到 null。 */
    @Test
    @DisplayName("签名段为空时 retCode 仍是 0000 但 cardData 为 null（已知地雷）")
    void 签名段为空时retCode仍是0000但无码体_属已知地雷() {
        RequestSignInsDataRespDTO signResp = new RequestSignInsDataRespDTO();
        signResp.setRetCode("0000");
        signResp.setIndustryDataSign("");
        when(securityClient.requestSignInsData(any())).thenReturn(signResp);

        IndustryCardDataBuildRespDTO response = service.buildCardData(baseRequest());
        assertEquals("0000", response.getRetCode(), "现状：下游 retCode 被原样回填");
        assertEquals(null, response.getCardData(), "但 cardData 一定没有值");
    }
}
