package com.chinasofti.huateng.paysign.port;

/**
 * 账户域 {@code queryPayChannelByContractNo} 应答的**窄视图** —— IF8A-75 直接解绑只读这三个字段。
 *
 * @param cardId      票卡号
 * @param cardType    票卡类型
 * @param thirdUserId 第三方用户号，仅在入参未带时用于回填
 */
public record AccountPayChannelView(String cardId, String cardType, String thirdUserId) {
}
