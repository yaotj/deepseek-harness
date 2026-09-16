package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractReqDTO;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractResult;
import com.chinasofti.huateng.model.app.SyncPayAccountIdReqDTO;

/**
 * 支付通道的<b>对内契约面</b>：只放「调用方是 pay-sign-server」的入口（ADR-D34）。
 *
 * <p>2026-09-11 从 {@link PayChannelService} 按<b>调用方</b>切出，方法体逐行照搬、行为不变。
 * 拆分维度是<b>契约面（Interface Segregation）</b>，不是渠道、也不是读写：</p>
 * <ul>
 *   <li><b>不能按渠道切</b>：原类里根本没有渠道 if-else，只有 IF8A-23 一处判钱包 {@code 0B}，
 *       切出来的两个类会有一个是空壳。</li>
 *   <li><b>不能按读写切</b>：IF8A-77 一次请求里既跨域读签约信息、又写两张表，
 *       按读写切会把一个业务动作劈成两半。</li>
 *   <li><b>按调用方切才成立</b>：这两个入口的**唯一**调用方是支付域（{@code PaySignClient} /
 *       {@code SignResultCommittedListener}），其余 5 个入口的调用方是 APP。两组的鉴权要求、
 *       报文口径、变更节奏都不同。</li>
 * </ul>
 *
 * <p><b>本接口不是 Facade</b>：它<b>不</b>转发给 {@link PayChannelService}，两者各自持
 * {@code UserPayChannelMapper}、互不依赖。<b>NEVER 把本接口改成对 {@code PayChannelService} 的包装</b>
 * —— 那样切分就只剩命名、依赖方向反而多一条。</p>
 *
 * <p><b>收益（不是「更整洁」这种说法）</b>：上线前补鉴权时只需在
 * {@code controller/internal/PayChannelInternalController} 一处加拦截，
 * 不必在 APP 端点上开例外；对齐 recon 的 {@code ReconInternalController} 现有形态。</p>
 *
 * <p><b>NEVER 把 APP 入口挪进来</b>（IF8A-23 / 24 / 77 / 75 解绑 / 钱包 requestAgreeRelease
 * 一律留在 {@link PayChannelService}），也 NEVER 把开户、销户、换号挪进来。</p>
 */
public interface PayChannelInternalService {

    /**
     * 按签约流水号查询支付通道（只读，供 pay-sign-server IF8A-75 反查票卡信息）。
     *
     * <p>为什么需要它：{@code APP_PAY_SIGN_INFO.CARD_ID} / {@code CARD_TYPE} 在本项目里
     * <b>全库为 NULL</b>（签约链路不写这两列），而 {@code APP_TERMINATION_REQUEST} 的同名列是
     * NOT NULL。IF8A-75 补建解约申请时只能从 {@code APP_USER_PAY_CHANNEL} 取，
     * 其 {@code REQ_CONTRACT_NO} 即签约流水号。</p>
     *
     * <p>未找到返回 8004，<b>NEVER</b> 返回 0000 带空 cardId——调用方靠 retCode 判定。</p>
     */
    QueryPayChannelByContractResult queryPayChannelByContractNo(QueryPayChannelByContractReqDTO request);

    /**
     * 接收支付域推来的 {@code PAY_ACCOUNT_ID}，回写 {@code APP_USER_PAY_CHANNEL}（ADR-D32）。
     *
     * <p><b>为什么需要它</b>：ADR-D30 把支付账号本地化成一列后，该列**唯一写入点是 IF8A-77**，
     * 于是「已签约但还没换过默认支付方式」的通道行该列恒为空、运营页面显示 {@code -}。
     * 由支付域在签约落库提交后推一次，这列才在签约那一刻就完整。这同时把边界从
     * 「账户域拉」补上了「支付域推」的另一半。</p>
     *
     * <p><b>本方法只写 {@code APP_USER_PAY_CHANNEL.PAY_ACCOUNT_ID} 一列。
     * NEVER 顺手改 {@code USER_ITP_REG_INFO.THIRD_PAY_ID}</b> —— 那一列是 IF8A-77
     * 「更换默认支付方式」这个**业务动作**的产物，签约成功时用户尚未做出该选择，
     * 在这里写它等于替用户决定了默认支付方式。</p>
     *
     * <p><b>命中 0 行返回 8004 而非失败</b>：「签约先于加通道」是合法时序，此刻账户域确实还没有
     * 对应通道行。调用方（支付域）MUST 把本接口整体当**允许失败**处理。</p>
     *
     * @param request 签约流水号 + 支付账号
     * @return {@code 0000} 已回写；{@code 8004} 未命中通道行；{@code 8001} 参数为空
     */
    CommonResult syncPayAccountId(SyncPayAccountIdReqDTO request);
}
