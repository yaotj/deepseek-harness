package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;

/**
 * 支付宝出行渠道的开卡申请（规范 3.69）。
 *
 * <p><b>⚠️ 本服务当前保留但不在链路上</b>：权威实现是 alipay-account-server 的
 * {@code AlipayAccountServiceImpl.requestApplication}（用户 2026-09-11 裁定：以 alipay-account 为准、
 * 旧代码保留不用，见 ADR-D15）。两套的入口 URL 与 DTO 完全相同，但落表与查重键不同——本实现落
 * {@code USER_ITP_REG_INFO}，那套落 {@code ALIPAY_USER_INFO}，<b>互相看不见</b>。
 * 因此 <b>NEVER 把任何模块的 {@code service.account.url} 指过来处理支付宝开卡</b>，
 * 否则同一 {@code thirdUserId} 会在两边各开一次户。</p>
 *
 * <p>2026-09-11（ADR-D31）从 {@code AccountRegistrationService} 拆出：它与 IF8A-01 是**两个渠道概念**，
 * 此前塞在一个类里靠方法名前缀区分。拆开的附带好处是这份「保留不用」的实现被隔离在自己的类里、
 * 不再与在跑的 IF8A-01 混编。<b>NEVER 把它合回去</b>。</p>
 */
public interface AlipayTripRegistrationService {

    /**
     * 支付宝出行-开卡申请。
     *
     * <p>编排顺序与 IF8A-01 一致：「查重 → 卡池预占 → 注册乘车状态 → 短事务落库 → 确认预占」，
     * 实现**不带事务**（体内全是 RPC）。</p>
     *
     * @param request 支付宝出行开卡申请参数
     * @return 开卡申请结果
     */
    AlipayTripRequestApplicationRespDTO alipayTripRequestApplication(AlipayTripRequestApplicationReqDTO request);
}
