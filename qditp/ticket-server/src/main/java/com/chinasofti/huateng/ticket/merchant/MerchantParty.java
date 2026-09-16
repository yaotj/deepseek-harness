package com.chinasofti.huateng.ticket.merchant;

/**
 * 商户号一对：资金归属方 + 收单方，**成对不可分割**。
 *
 * <p>存在理由不是「代码更漂亮」，而是让**错配在编译期不可表达**。
 * 2026-09-14 之前调用方拿到的是 {@link MerchantPartyResolver} 的四个单字段 getter
 * （old / new × attributable / receiving）外加一个 {@code shouldUseOldMerchant(rideDate)} 布尔，
 * 于是「老归属方 + 新收单方」这种混搭在语法上完全合法、编译器与单测都拦不住，
 * 而**商户号配错在数据里事后完全看不出来** —— 库里就是两个正常的商户号字符串，
 * 只有对账时资金流向不上才会暴露，且届时已无法区分是配置错还是代码错。
 * 这条风险目前是放大的：{@code app.trans.merchant-change-date=20260901} 仍未经业务确认（P0）。</p>
 *
 * <p>因此 {@code MerchantPartyResolver} 只提供三个返回本类型的入口
 * （{@code resolveFor(rideDate)} 按乘车日期选、{@code oldParty()} / {@code newParty()} 按业务规则强制选），
 * 四个单字段 getter 已整体删除、{@code shouldUseOldMerchant(rideDate)} 已由 public 降级为 private。
 * <b>NEVER 把它们改回 public</b>，
 * 也 <b>NEVER 给本 record 加只取一个字段的便利方法</b> —— 那等于把混搭能力还给调用方。</p>
 *
 * <p>本类与 {@code trans-query-server} 的 {@code transquery/merchant/MerchantParty} 是两份副本
 * （与 {@link MerchantPartyResolver} 同一处境），除包名与本段说明外内容相同，
 * 改一处 MUST 同批改另一处。</p>
 *
 * @param attributableParty 资金归属方商户号
 * @param receivingParty    收单方商户号
 * @param useOld            本对取的是变更日期之前的老商户号（仅用于日志取证，不参与业务判断）
 */
public record MerchantParty(String attributableParty, String receivingParty, boolean useOld) {
}
