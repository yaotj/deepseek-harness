package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** IF5A-03 票卡更新所需的账户域两跳查询：{@code queryCardTypeByCardId} → {@code queryUserInfo}。 */
@Component
class SupplementUserLookup {

    @Autowired
    private AccountClient accountClient;

    /** 查询用户信息。 */
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
