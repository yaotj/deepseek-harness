package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * IF5A-03 票卡更新所需的账户域两跳查询：{@code queryCardTypeByCardId} → {@code queryUserInfo}。
 *
 * <p>2026-09-14（ADR-D71）从 {@code CardDataUpdateHandler} 迁出。迁出理由是这段与 IF5A-03 的
 * 业务语义无关——它做的只是「按 cardId 拿到 thirdUserId / cardType / channel」，而那个 Handler
 * 剩下的部分全是补站状态机与报文装配。</p>
 *
 * <p><b>NEVER 把本类与 {@code CardDataAnalyseHandler.queryUserInfo} 合并。</b>那一份是 IF5A-01 的，
 * 契约不同：非支付宝发行方**刻意不做第二跳**（{@code queryCardTypeByCardId} 的返回里已带
 * msisdn 与 regTms），且失败处置是抛异常而不是三态返回。两份看着像，合并会同时改坏两个接口。</p>
 *
 * <p>本类只被 {@code CardDataUpdateHandler} 调用，包级可见，<b>包外 NEVER 注入</b>。</p>
 */
@Component
class SupplementUserLookup {

    @Autowired
    private AccountClient accountClient;

    /**
     * 查询用户信息。
     *
     * <p>审查项 M004：原实现把「未注册用户」「account-server 连不上」「响应缺 thirdUserId」
     * 三种情况一律 {@code throw new RuntimeException} 再在外层压成 {@code INVALID_PARAM(8001)}，
     * BOM 只能看到「请求参数验证失败」。现在按 {@link RpcOutcome} 三态返回，由调用方分码。
     * <b>NEVER 退回抛 RuntimeException。</b></p>
     */
    Result query(String cardId) {
        QueryUserInfoResult cardTypeResult;
        try {
            cardTypeResult = accountClient.queryCardTypeByCardId(cardId);
        } catch (Exception e) {
            return new Result(null, new RpcOutcome.Unreachable(e));
        }
        if (cardTypeResult == null) {
            return new Result(null, new RpcOutcome.BizRejected(null, "查询用户卡类型无响应体"));
        }
        if (!SupplementCodec.RET_SUCCESS.equals(cardTypeResult.getRetCode())) {
            return new Result(null,
                    new RpcOutcome.BizRejected(cardTypeResult.getRetCode(), cardTypeResult.getRetMsg()));
        }
        if (!StringUtils.hasText(cardTypeResult.getThirdUserId())) {
            return new Result(null, new RpcOutcome.BizRejected(null, "未注册用户"));
        }

        QueryUserInfoReqDTO userInfoReq = new QueryUserInfoReqDTO();
        userInfoReq.setThirdUserId(cardTypeResult.getThirdUserId());
        userInfoReq.setCardType(cardTypeResult.getCardType());
        userInfoReq.setCardId(cardId);
        QueryUserInfoResult userInfo;
        try {
            userInfo = accountClient.queryUserInfo(userInfoReq);
        } catch (Exception e) {
            return new Result(null, new RpcOutcome.Unreachable(e));
        }
        if (userInfo == null) {
            return new Result(null, new RpcOutcome.BizRejected(null, "查询用户详细信息无响应体"));
        }
        if (!SupplementCodec.RET_SUCCESS.equals(userInfo.getRetCode())
                || !StringUtils.hasText(userInfo.getThirdUserId())) {
            return new Result(null, new RpcOutcome.BizRejected(userInfo.getRetCode(), userInfo.getRetMsg()));
        }
        return new Result(userInfo, new RpcOutcome.Ok());
    }

    /** 用户信息查询结果，{@code userInfo} 仅在 {@code outcome} 为 {@code Ok} 时非空。 */
    record Result(QueryUserInfoResult userInfo, RpcOutcome outcome) {
    }
}
