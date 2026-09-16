package com.chinasofti.huateng.paysign.port;

/**
 * 账户域 {@code queryPayChannelByContractNo} 应答的**窄视图** —— IF8A-75 直接解绑只读这三个字段。
 *
 * <p><b>为什么这三列必须从账户域取</b>：`APP_TERMINATION_REQUEST` 的 {@code CARD_ID} /
 * {@code CARD_TYPE} 是 NOT NULL，而支付域自己的 `APP_PAY_SIGN_INFO` 同名两列**全库为 NULL**
 * （签约链路从不写它们）。票卡信息的权威来源是账户域的 `APP_USER_PAY_CHANNEL`，
 * 其 {@code REQ_CONTRACT_NO} 即 {@code requestSignSeq}。这一步一旦丢，补建解约申请必然
 * {@code ORA-01400}（2026-09-08 实测）。
 *
 * @param cardId      票卡号
 * @param cardType    票卡类型
 * @param thirdUserId 第三方用户号，仅在入参未带时用于回填
 */
public record AccountPayChannelView(String cardId, String cardType, String thirdUserId) {
}
