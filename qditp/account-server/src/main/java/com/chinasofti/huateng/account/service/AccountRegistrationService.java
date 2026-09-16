package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;

/**
 * 开户发号：<b>IF8A-01 APP 渠道开户</b>，只有这一个入口。
 *
 * <p>2026-09-11 第五轮拆分从 {@code AccountApplicationService} 整段搬出，方法签名与语义一律不变；
 * 唯一调用方是 {@code RequestApplicationController}。</p>
 *
 * <p>2026-09-11（ADR-D31）<b>支付宝出行开卡已迁到 {@link AlipayTripRegistrationService}</b>：
 * 它与 IF8A-01 是两个渠道概念，此前塞在同一个类里靠方法名前缀区分，导致两套平行 helper。
 * 落库三步（注册乘车状态 / 双表短事务 / 挂员工码）收口在渠道无关的
 * {@link RegistrationCommitService}。<b>NEVER 把支付宝入口挪回本接口</b>。</p>
 *
 * <p><b>NEVER 把销户、查询、HCE、换号、支付通道挪进本接口</b>：它们分别属
 * {@code AccountApplicationService}、{@code PhoneChangeService}、{@code PayChannelService}。</p>
 */
public interface AccountRegistrationService {

    /**
     * IF8A-01 请求开户。
     *
     * <p>编排顺序是「预占卡号 → 注册乘车状态 → 短事务落库 → 确认预占」，实现**不带事务**，
     * 详见 {@code AccountRegistrationServiceImpl#requestApplication} 的方法注释。</p>
     *
     * @param request 请求开户参数
     * @return 请求开户结果
     */
    RequestApplicationResult requestApplication(RequestApplicationReqDTO request);
}
