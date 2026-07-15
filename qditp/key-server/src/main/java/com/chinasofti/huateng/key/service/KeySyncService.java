package com.chinasofti.huateng.key.service;

import com.chinasofti.huateng.model.app.RequestKeyListReqDTO;
import com.chinasofti.huateng.model.app.RequestKeyListResult;

/**
 * IF8A-02 请求同步密钥业务服务。
 *
 * <p>该服务负责把 APP 请求中的用户与卡信息转换为密钥生成所需参数，
 * 并协调 CA 密钥仓库、acc-security-server 和本地 3DES 转加密逻辑生成最终返回给 APP 的 keyList。</p>
 */
public interface KeySyncService {
    /**
     * 生成用户同步密钥列表。
     *
     * @param request 请求同步密钥业务参数
     * @return 请求同步密钥应答
     */
    RequestKeyListResult requestKeyList(RequestKeyListReqDTO request);
}
