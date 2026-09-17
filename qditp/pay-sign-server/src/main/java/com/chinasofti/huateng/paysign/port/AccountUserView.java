package com.chinasofti.huateng.paysign.port;

/**
 * 账户域 {@code queryUserInfo} 应答的**窄视图** —— 只保留支付域真正读的三个字段。
 *
 * @param channel       支付通道（即 {@code paymentVendor}），账户域侧列名 {@code CHANNEL}
 * @param thirdPayId    第三方支付账号，钱包（{@code 0B}）分支据它判定是否已绑定
 * @param reqContractNo 签约流水号，非钱包分支据它补 {@code requestSignSeq}
 */
public record AccountUserView(String channel, String thirdPayId, String reqContractNo) {
}
