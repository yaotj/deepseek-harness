package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** IF5A-03 用户信息查询的特征测试：account 域两跳 + 支付宝出行账户域回落。 */
class SupplementUserLookupTest {

    private static final String CARD_ID = "0426090949000069";

    private AccountClient accountClient;
    private AlipayAccountClient alipayAccountClient;
    private SupplementUserLookup lookup;

    @BeforeEach
    void setUp() {
        accountClient = mock(AccountClient.class);
        alipayAccountClient = mock(AlipayAccountClient.class);

        lookup = new SupplementUserLookup();
        ReflectionTestUtils.setField(lookup, "accountClient", accountClient);
        ReflectionTestUtils.setField(lookup, "alipayAccountClient", alipayAccountClient);
    }

    private QueryUserInfoResult accountResult(String thirdUserId) {
        QueryUserInfoResult result = new QueryUserInfoResult();
        result.setRetCode("0000");
        result.setThirdUserId(thirdUserId);
        result.setCardType("0441");
        return result;
    }

    private AlipayUserInfoDTO alipayUser(String thirdUserId) {
        AlipayUserInfoDTO user = new AlipayUserInfoDTO();
        user.setCardId(CARD_ID);
        user.setThirdUserId(thirdUserId);
        user.setCardType("01");
        user.setChannel("07");
        return user;
    }

    @Test
    @DisplayName("account 域两跳都命中：不回落支付宝")
    void account域命中时不回落支付宝() {
        when(accountClient.queryCardTypeByCardId(CARD_ID)).thenReturn(accountResult("00000123"));
        QueryUserInfoResult detail = accountResult("00000123");
        detail.setChannel("01");
        when(accountClient.queryUserInfo(any(QueryUserInfoReqDTO.class))).thenReturn(detail);

        SupplementUserLookup.Result result = lookup.query(CARD_ID);

        assertTrue(result.outcome().isOk());
        assertEquals("00000123", result.userInfo().getThirdUserId());
        assertEquals("01", result.userInfo().getChannel());
        verifyNoInteractions(alipayAccountClient);
    }

    @Test
    @DisplayName("account 域查不到 thirdUserId：回落支付宝账户域并返回 Ok")
    void account域查不到时回落支付宝() {
        when(accountClient.queryCardTypeByCardId(CARD_ID)).thenReturn(accountResult(null));
        when(alipayAccountClient.selectByCardId(CARD_ID)).thenReturn(alipayUser("2088ALIPAYUSER"));

        SupplementUserLookup.Result result = lookup.query(CARD_ID);

        assertTrue(result.outcome().isOk(), "支付宝出行用户在 account 域恒查不到，回落是常态路径而非兜底");
        assertEquals("2088ALIPAYUSER", result.userInfo().getThirdUserId());
        assertEquals("07", result.userInfo().getChannel());
        verify(alipayAccountClient).selectByCardId(CARD_ID);
    }

    @Test
    @DisplayName("account 域查不到且支付宝域也查不到：仍以未注册用户拒绝整笔")
    void bothDomainsMissRejectsAsUnregistered() {
        when(accountClient.queryCardTypeByCardId(CARD_ID)).thenReturn(accountResult(null));
        when(alipayAccountClient.selectByCardId(CARD_ID)).thenReturn(null);

        SupplementUserLookup.Result result = lookup.query(CARD_ID);

        assertFalse(result.outcome().isOk());
        RpcOutcome.BizRejected rejected = assertInstanceOf(RpcOutcome.BizRejected.class, result.outcome());
        assertEquals("未注册用户", rejected.retMsg());
    }

    @Test
    @DisplayName("支付宝域抛异常：归为 Unreachable，NEVER 退化成未注册用户")
    void alipayDomainThrowsIsUnreachable() {
        when(accountClient.queryCardTypeByCardId(CARD_ID)).thenReturn(accountResult(null));
        when(alipayAccountClient.selectByCardId(CARD_ID)).thenThrow(new RuntimeException("connection reset"));

        SupplementUserLookup.Result result = lookup.query(CARD_ID);

        assertFalse(result.outcome().isOk());
        assertInstanceOf(RpcOutcome.Unreachable.class, result.outcome(),
                "网络不可达 MUST 与业务拒绝区分：前者可重试，后者一次即终态");
    }

    @Test
    @DisplayName("支付宝域查到但 thirdUserId 为空：按未注册用户拒绝")
    void alipayHitWithoutThirdUserIdRejects() {
        when(accountClient.queryCardTypeByCardId(CARD_ID)).thenReturn(accountResult(null));
        when(alipayAccountClient.selectByCardId(CARD_ID)).thenReturn(alipayUser(null));

        SupplementUserLookup.Result result = lookup.query(CARD_ID);

        assertFalse(result.outcome().isOk());
        assertInstanceOf(RpcOutcome.BizRejected.class, result.outcome());
    }
}
