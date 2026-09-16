package com.chinasofti.huateng.paysign.port;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractReqDTO;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractResult;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.SyncPayAccountIdReqDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link AccountDomainPort} 的唯一实现：rpc DTO 装配 + {@code retCode} → {@link RpcOutcome} 翻译。
 *
 * <p><b>这是 pay-sign-server 里唯一允许出现 {@code accountClient} 写调用的地方</b>
 * （读调用暂留在原处，见接口 Javadoc）。<b>NEVER 在业务类里重新 new 那些 {@code *ReqDTO}</b> ——
 * 那正是 ADR-D46 要消掉的散落点。</p>
 *
 * <p><b>本类的每个方法都 NEVER 抛异常</b>：连不上 / 超时 / HTTP 错误统一收成
 * {@link RpcOutcome.Unreachable} 并带上原始 cause；响应为空按 {@link RpcOutcome.BizRejected} 处理
 * （HTTP 已 2xx，是对端契约问题，重推同一报文不会变好）。</p>
 *
 * <p><b>日志刻意留在本类</b>：改造前每个调用点各打一遍「call account xxx request/response」，
 * 收口后请求与响应只在这里打一次，业务类只打自己的判定结论。</p>
 */
@Component
public class AccountDomainRpcAdapter implements AccountDomainPort {

    private static final Logger log = LoggerFactory.getLogger(AccountDomainRpcAdapter.class);

    /** 账户域的成功码，与本模块对外应答复用同一个枚举值，NEVER 写成裸字面量 {@code "0000"}。 */
    private static final String SUCCESS_CODE = PaySignErrorCodeEnum.SUCCESS.getCode();

    private final AccountClient accountClient;

    public AccountDomainRpcAdapter(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    @Override
    public RpcOutcome removeChannel(String thirdUserId, String paymentVendor, String cardId, String cardType) {
        RequestRemovePayChannelReqDTO request = buildRemoveRequest(thirdUserId, paymentVendor, cardId, cardType);
        try {
            log.info("call account removePayChannel request={}", JSON.toJSONString(request));
            RequestRemovePayChannelResult response = accountClient.requestRemovePayChannel(request);
            log.info("call account removePayChannel response={}", JSON.toJSONString(response));
            return toOutcome(response);
        } catch (Exception e) {
            log.error("call account removePayChannel 未获业务答复（可重试）, thirdUserId={}", thirdUserId, e);
            return new RpcOutcome.Unreachable(e);
        }
    }

    @Override
    public RpcOutcome agreeRelease(String thirdUserId, String paymentVendor, String cardId, String cardType) {
        RequestRemovePayChannelReqDTO request = buildRemoveRequest(thirdUserId, paymentVendor, cardId, cardType);
        try {
            log.info("call account agreeRelease request={}", JSON.toJSONString(request));
            RequestRemovePayChannelResult response = accountClient.requestAgreeRelease(request);
            log.info("call account agreeRelease response={}", JSON.toJSONString(response));
            return toOutcome(response);
        } catch (Exception e) {
            log.error("call account agreeRelease 未获业务答复（可重试）, thirdUserId={}", thirdUserId, e);
            return new RpcOutcome.Unreachable(e);
        }
    }

    @Override
    public RpcOutcome syncPayAccountId(String reqContractNo, String payAccountId) {
        SyncPayAccountIdReqDTO request = new SyncPayAccountIdReqDTO();
        request.setReqContractNo(reqContractNo);
        request.setPayAccountId(payAccountId);
        try {
            CommonResult response = accountClient.syncPayAccountId(request);
            if (response == null) {
                return new RpcOutcome.BizRejected(null, "账户域响应为空");
            }
            return RpcOutcome.ofRetCode(response.getRetCode(), response.getRetMsg());
        } catch (Exception e) {
            log.warn("call account syncPayAccountId 未获业务答复, reqContractNo={}", reqContractNo, e);
            return new RpcOutcome.Unreachable(e);
        }
    }

    /** 两个移除端点共用同一个入向 DTO，装配也只写一遍。 */
    private RequestRemovePayChannelReqDTO buildRemoveRequest(String thirdUserId, String paymentVendor,
                                                            String cardId, String cardType) {
        RequestRemovePayChannelReqDTO request = new RequestRemovePayChannelReqDTO();
        request.setThirdUserId(thirdUserId);
        request.setCardId(cardId);
        request.setCardType(cardType);
        request.setChannel(paymentVendor);
        return request;
    }

    @Override
    public AccountQuery<AccountUserView> queryUser(String thirdUserId, String cardId, String cardType) {
        QueryUserInfoReqDTO request = new QueryUserInfoReqDTO();
        request.setThirdUserId(thirdUserId);
        request.setCardId(cardId);
        request.setCardType(cardType);
        try {
            QueryUserInfoResult response = accountClient.queryUserInfo(request);
            if (response == null) {
                log.warn("call account queryUserInfo 响应为空, thirdUserId={}, cardId={}", thirdUserId, cardId);
                return new AccountQuery.NotFound<>(null, "账户域响应为空");
            }
            if (!SUCCESS_CODE.equals(response.getRetCode())) {
                log.info("call account queryUserInfo 业务未命中, thirdUserId={}, cardId={}, retCode={}, retMsg={}",
                        thirdUserId, cardId, response.getRetCode(), response.getRetMsg());
                return new AccountQuery.NotFound<>(response.getRetCode(), response.getRetMsg());
            }
            return new AccountQuery.Found<>(new AccountUserView(
                    trimToNull(response.getChannel()),
                    trimToNull(response.getThirdPayId()),
                    trimToNull(response.getReqContractNo())));
        } catch (Exception e) {
            log.error("call account queryUserInfo 未获业务答复（可重试）, thirdUserId={}, cardId={}",
                    thirdUserId, cardId, e);
            return new AccountQuery.Unreachable<>(e);
        }
    }

    @Override
    public AccountQuery<AccountPayChannelView> queryPayChannelByContract(String reqContractNo) {
        QueryPayChannelByContractReqDTO request = new QueryPayChannelByContractReqDTO();
        request.setReqContractNo(reqContractNo);
        try {
            QueryPayChannelByContractResult response = accountClient.queryPayChannelByContractNo(request);
            if (response == null) {
                log.warn("call account queryPayChannelByContractNo 响应为空, reqContractNo={}", reqContractNo);
                return new AccountQuery.NotFound<>(null, "账户域响应为空");
            }
            if (!SUCCESS_CODE.equals(response.getRetCode())) {
                log.warn("call account queryPayChannelByContractNo 业务未命中, reqContractNo={}, retCode={}, retMsg={}",
                        reqContractNo, response.getRetCode(), response.getRetMsg());
                return new AccountQuery.NotFound<>(response.getRetCode(), response.getRetMsg());
            }
            log.info("call account queryPayChannelByContractNo 已取到票卡信息, reqContractNo={}, cardId={}, cardType={}",
                    reqContractNo, response.getCardId(), response.getCardType());
            return new AccountQuery.Found<>(new AccountPayChannelView(
                    trimToNull(response.getCardId()),
                    trimToNull(response.getCardType()),
                    trimToNull(response.getThirdUserId())));
        } catch (Exception e) {
            log.error("call account queryPayChannelByContractNo 未获业务答复（可重试）, reqContractNo={}",
                    reqContractNo, e);
            return new AccountQuery.Unreachable<>(e);
        }
    }

    /**
     * 空白串一律收成 {@code null}，让调用点只需判 {@code null}。
     *
     * <p>改造前三处调用点各写一遍 {@code StringUtils.hasText(...) ? x.trim() : 不填}，
     * 收口到这里后调用点只剩业务判断。<b>NEVER 改成保留原样</b> ——
     * 账户域返回过带空格的 {@code channel}，`normalizeVendor` 之前的比较会因此错判。
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private RpcOutcome toOutcome(RequestRemovePayChannelResult response) {
        if (response == null) {
            return new RpcOutcome.BizRejected(null, "账户域响应为空");
        }
        return RpcOutcome.ofRetCode(response.getRetCode(), response.getRetMsg());
    }
}
