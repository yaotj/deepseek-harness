package com.chinasofti.huateng.wallet.service;

import com.chinasofti.huateng.wallet.model.sign.RequestSignInfoReqDTO;
import com.chinasofti.huateng.wallet.model.sign.RequestSignInfoRespDTO;

public interface WalletSignService {
    /**
     * 生成请求签约所需的 SDK 拉起参数。
     */
    RequestSignInfoRespDTO requestSignInfo(RequestSignInfoReqDTO request);
}
