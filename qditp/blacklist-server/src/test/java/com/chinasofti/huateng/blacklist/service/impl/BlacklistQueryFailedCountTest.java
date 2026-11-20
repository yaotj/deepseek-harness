package com.chinasofti.huateng.blacklist.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.CardFailedCountRespDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * IT-087：queryBlackList 回填 failedCount（按 cardId 全量 FAIL/RETRY，仅 inBlack=1 时查，fail-safe 回落 0）。
 */
class BlacklistQueryFailedCountTest {

    private final BlacklistServiceImpl service = new BlacklistServiceImpl();
    private final GateTxnPayClient gateTxnPayClient = mock(GateTxnPayClient.class);
    private final BlacklistMapperStub mapper = new BlacklistMapperStub();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "blacklistMapper", mapper.delegate());
        ReflectionTestUtils.setField(service, "gateTxnPayClient", gateTxnPayClient);
    }

    @Test
    void inBlackReturnsFailedCountFromGate() {
        mapper.setCountByCardIds(1); // inBlack=1
        when(gateTxnPayClient.countFailedOrderByCard(any())).thenReturn(failResp("0000", 5));

        QueryBlackListResult result = service.queryBlackList(req("0426090949000508"));

        assertEquals("1", result.getInBlack());
        assertEquals(5, result.getFailedCount(), "inBlack=1 时 MUST 回填 gate-txn-pay 的失败订单数");
    }

    @Test
    void notInBlackKeepsZeroAndNoGateCall() {
        mapper.setCountByCardIds(0); // inBlack=0

        QueryBlackListResult result = service.queryBlackList(req("0426090949000000"));

        assertEquals("0", result.getInBlack());
        assertEquals(0, result.getFailedCount(), "非黑名单卡 failedCount 恒 0、MUST 不触发跨模块查询");
        verify(gateTxnPayClient, never()).countFailedOrderByCard(any());
    }

    @Test
    void gateUnsuccessfulAnswerFallsBackToZero() {
        mapper.setCountByCardIds(1);
        when(gateTxnPayClient.countFailedOrderByCard(any())).thenReturn(failResp("8002", 0));

        QueryBlackListResult result = service.queryBlackList(req("0426090949000508"));

        assertEquals(0, result.getFailedCount(), "gate 未答成功 MUST fail-safe 回落 0、MUST NOT 抛错");
        assertEquals("1", result.getInBlack(), "inBlack 与 failedCount 相互独立，回落 0 不改变 inBlack");
    }

    @Test
    void gateRpcExceptionFallsBackToZero() {
        mapper.setCountByCardIds(1);
        when(gateTxnPayClient.countFailedOrderByCard(any())).thenThrow(new RuntimeException("boom"));

        QueryBlackListResult result = service.queryBlackList(req("0426090949000508"));

        assertEquals(0, result.getFailedCount(), "gate rpc 异常 MUST fail-safe 回落 0、MUST NOT 影响黑名单查询");
        assertEquals("1", result.getInBlack());
    }

    @Test
    void multiCardSumsFailedCount() {
        mapper.setCountByCardIds(1);
        when(gateTxnPayClient.countFailedOrderByCard(any()))
                .thenReturn(failResp("0000", 3))
                .thenReturn(failResp("0000", 2));

        QueryBlackListResult result = service.queryBlackList(req("cardA,cardB"));

        assertEquals(5, result.getFailedCount(), "多卡查询 MUST 累加各卡失败数");
    }

    private QueryBlackListReqDTO req(String cardId) {
        QueryBlackListReqDTO request = new QueryBlackListReqDTO();
        request.setCardId(cardId);
        return request;
    }

    private CardFailedCountRespDTO failResp(String code, int count) {
        CardFailedCountRespDTO resp = new CardFailedCountRespDTO();
        resp.setResultCode(code);
        resp.setFailedCount(count);
        return resp;
    }

    /** 最小 BlacklistMapper 替身：只桩 countByCardIds，其余不触碰。 */
    static class BlacklistMapperStub {
        private final com.chinasofti.huateng.blacklist.mapper.BlacklistMapper mapper =
                mock(com.chinasofti.huateng.blacklist.mapper.BlacklistMapper.class);
        private int count = 0;

        com.chinasofti.huateng.blacklist.mapper.BlacklistMapper delegate() {
            return mapper;
        }

        void setCountByCardIds(int count) {
            this.count = count;
            when(mapper.countByCardIds(any())).thenReturn(count);
        }
    }
}
