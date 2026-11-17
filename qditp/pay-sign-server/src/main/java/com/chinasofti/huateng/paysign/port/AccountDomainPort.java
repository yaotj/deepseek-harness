package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.rpc.outcome.RpcOutcome;

public interface AccountDomainPort {

    AccountQuery<AccountUserView> queryUser(String thirdUserId, String cardId, String cardType);

    AccountQuery<AccountPayChannelView> queryPayChannelByContract(String reqContractNo);

    RpcOutcome removeChannel(String thirdUserId, String paymentVendor, String cardId, String cardType);

    RpcOutcome agreeRelease(String thirdUserId, String paymentVendor, String cardId, String cardType);

    RpcOutcome syncPayAccountId(String reqContractNo, String payAccountId);
}
