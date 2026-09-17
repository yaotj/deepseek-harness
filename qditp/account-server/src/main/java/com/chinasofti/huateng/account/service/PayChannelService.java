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
     * @param request 请求删除支付通道参数
     * @return 请求删除支付通道结果
     */
    RequestRemovePayChannelResult requestRemovePayChannel(RequestRemovePayChannelReqDTO request);

    /**
     * 钱包 requestAgreeRelease 兼容入口，语义为删除本地钱包支付通道。
     */
    RequestRemovePayChannelResult requestAgreeRelease(RequestRemovePayChannelReqDTO request);
}
