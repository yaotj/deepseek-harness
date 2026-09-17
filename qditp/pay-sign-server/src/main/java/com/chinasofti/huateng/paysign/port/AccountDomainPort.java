package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.rpc.outcome.RpcOutcome;

/** 支付域看账户域的**窄接口**（防腐层），只暴露本域真正需要的三个**写**动作。 */
public interface AccountDomainPort {

    /** 按用户 + 票卡查账户域注册信息，供钱包绑定状态查询与免密扣款补参两处使用。 */
    AccountQuery<AccountUserView> queryUser(String thirdUserId, String cardId, String cardType);

    /** 按签约流水号反查账户域支付通道上的票卡信息（IF8A-75 直接解绑用）。 */
    AccountQuery<AccountPayChannelView> queryPayChannelByContract(String reqContractNo);

    /** 解约成功后清理账户域的支付通道（IF8A-75 同语义的内部调用）。 */
    RpcOutcome removeChannel(String thirdUserId, String paymentVendor, String cardId, String cardType);

    /** 钱包（{@code paymentVendor=0B}）误走 {@code requestTermination} 时的兼容路径：只移除本地支付通道。 */
    RpcOutcome agreeRelease(String thirdUserId, String paymentVendor, String cardId, String cardType);

    /** 把签约结果里的支付账号回写到账户域（展示值）。 */
    RpcOutcome syncPayAccountId(String reqContractNo, String payAccountId);
}
