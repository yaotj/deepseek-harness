package com.chinasofti.huateng.transquery.merchant;

/**
 * 商户号一对：资金归属方 + 收单方，成对不可分割。
 *
 * @param attributableParty 资金归属方商户号
 * @param receivingParty 收单方商户号
 * @param useOld 本对取的是变更日期之前的老商户号（仅用于日志取证，不参与业务判断）
 */
public record MerchantParty(String attributableParty, String receivingParty, boolean useOld) {
}
