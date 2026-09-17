package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractReqDTO;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractResult;
import com.chinasofti.huateng.model.app.SyncPayAccountIdReqDTO;

/**
 * 支付通道的<b>对内契约面</b>：只放「调用方是 pay-sign-server」的入口（ADR-D34）。
 */
public interface PayChannelInternalService {
    /**
     * 按签约流水号查询支付通道（只读，供 pay-sign-server IF8A-75 反查票卡信息）。
     */
    QueryPayChannelByContractResult queryPayChannelByContractNo(QueryPayChannelByContractReqDTO request);

    /**
     * 接收支付域推来的 {@code PAY_ACCOUNT_ID}，回写 {@code APP_USER_PAY_CHANNEL}（ADR-D32）。
     *
     * @param request 签约流水号 + 支付账号
     * @return {@code 0000} 已回写；{@code 8004} 未命中通道行；{@code 8001} 参数为空
     */
    CommonResult syncPayAccountId(SyncPayAccountIdReqDTO request);
}
