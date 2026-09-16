package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractReqDTO;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractResult;

/**
 * 支付通道的 <b>APP 契约面</b>（{@code APP_USER_PAY_CHANNEL} 的增删改 + 开户记录上的默认通道字段）。
 *
 * <p>2026-09-11 第四轮拆分从 {@code AccountApplicationService} 整段搬出，方法签名与语义一律不变。</p>
 *
 * <p><b>2026-09-11（ADR-D34）按调用方切出对内契约面</b>：{@code queryPayChannelByContractNo} 与
 * {@code syncPayAccountId} 这两个「调用方是 pay-sign-server」的入口已迁到
 * {@link PayChannelInternalService}，本接口只剩 5 个 <b>调用方是 APP</b> 的入口，
 * 调用方也因此只有 {@code RequestApplicationController} 一处。
 * <b>NEVER 把那两个入口迁回来</b> —— 混在一起时「补鉴权」没有单一落点，
 * 只能在 APP 端点上开例外。</p>
 *
 * <p><b>NEVER 把开户发号、销户、换号挪进本接口</b>：那三块分别属
 * {@code AccountRegistrationService} / {@code AccountArchiveService} / {@code PhoneChangeService}。</p>
 */
public interface PayChannelService {

    /**
     * IF8A-23 请求添加支付通道。
     *
     * @param request 请求添加支付通道参数
     * @return 请求添加支付通道结果
     */
    RequestAddPayChannelResult requestAddPayChannel(RequestAddPayChannelReqDTO request);

    /**
     * IF8A-24 请求设置默认支付通道。
     *
     * @param request 请求设置默认支付通道参数
     * @return 请求设置默认支付通道结果
     */
    RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(RequestSetDefaultPayChannelReqDTO request);

    /**
     * IF8A-77 更换第三方渠道码默认支付方式。
     *
     * @param request 更换第三方渠道码默认支付方式参数
     * @return 更换第三方渠道码默认支付方式结果
     */
    RequestUpdateChannelDefaultContractResult requestUpdateChannelDefaultContract(RequestUpdateChannelDefaultContractReqDTO request);

    /**
     * 请求删除支付通道（IF8A-75 解约成功后回调）。
     *
     * <p>删掉最后一个渠道时会同事务调 {@code AccountArchiveService.archiveIfLastChannelRemoved}，
     * 归档失败即整单回滚返错误码让上游重试，<b>NEVER 留「通道已删、归档未做」的半成品</b>。</p>
     *
     * @param request 请求删除支付通道参数
     * @return 请求删除支付通道结果
     */
    RequestRemovePayChannelResult requestRemovePayChannel(RequestRemovePayChannelReqDTO request);

    /** 钱包 requestAgreeRelease 兼容入口，语义为删除本地钱包支付通道。 */
    RequestRemovePayChannelResult requestAgreeRelease(RequestRemovePayChannelReqDTO request);
}
