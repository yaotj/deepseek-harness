package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;

/**
 * 支付宝渠道**解约聚合**的唯一入口（2026-09-18 从 4 方法门面 {@code AlipayContractService} 拆出）。
 *
 * <p>实现是 {@code service/impl/termination/TerminationCoordinator}：登记走
 * {@code TerminationRegistrationService}（全模块**唯一**带 {@code @Transactional} 的方法），
 * 执行走 {@code TerminationNotifier} 的三态 {@code Outcome}。
 * <b>NEVER 在本接口的实现里重新实现一遍通知与状态收口</b>（ADR-D129）。
 *
 * <p>与 {@link AlipaySignContractService} 的分工见那个接口的类注释：**两个聚合的依赖簇不相交**，
 * 合回一个门面就等于退回拆分前。批量销卡（按状态扫描重推）另属
 * {@code AlipayTerminationInternalService}，不在本接口内。
 */
public interface AlipayTerminationService {

    /** 解约登记（{@code POST /channel/terminateContract}）：只落 {@code PENDING} 登记行，不出网。 */
    AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request);

    /**
     * 立即执行一条解约（{@code GET /internal/alipay/termination/execute}，入口是管理台）：缺登记行会先补登记。
     *
     * <p>2026-09-18：URL 由 {@code GET /channel/executeTermination} 迁至 internal 前缀，
     * 宿主改为 {@code controller/internal/AlipayTerminationInternalController}，<b>旧路径已删除、无别名</b>。
     */
    AlipayCommonResponse executeTermination(String agreementCode);
}
