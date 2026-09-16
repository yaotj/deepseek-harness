package com.chinasofti.huateng.paysign.port;

/**
 * 账户域 {@code queryUserInfo} 应答的**窄视图** —— 只保留支付域真正读的三个字段。
 *
 * <p>rpc 侧的 {@code QueryUserInfoResult} 有 12 个字段（{@code itpCardType} / {@code cardIssueCode} /
 * {@code hceData} / {@code msisdn} / {@code regTms} / {@code companionFlag} …），支付域一个都不看。
 * 收窄到三个字段是端口存在的意义：<b>NEVER 把字段加回来「以备将来」</b> ——
 * 端口一旦变宽就退化成 rpc DTO 的别名，防腐层也就没了。
 *
 * <p><b>不含 {@code retCode}</b>：成功与否由 {@link AccountQuery} 的分支表达，
 * 拿到 {@code Found} 就意味着账户域已答 {@code 0000}。把 {@code retCode} 塞进视图会诱导调用点
 * 再判一次，那正是 ADR-D94 里「两个调用点对同一个 DTO 给出相反答案」的成因。
 *
 * @param channel       支付通道（即 {@code paymentVendor}），账户域侧列名 {@code CHANNEL}
 * @param thirdPayId    第三方支付账号，钱包（{@code 0B}）分支据它判定是否已绑定
 * @param reqContractNo 签约流水号，非钱包分支据它补 {@code requestSignSeq}
 */
public record AccountUserView(String channel, String thirdPayId, String reqContractNo) {
}
