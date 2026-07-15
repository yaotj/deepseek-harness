package com.chinasofti.huateng.key.controller;

import com.chinasofti.huateng.key.service.KeySyncService;
import com.chinasofti.huateng.model.app.RequestKeyListReqDTO;
import com.chinasofti.huateng.model.app.RequestKeyListResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 密钥服务内部接口入口。
 *
 * <p>当前只承接 IF8A-02 请求同步密钥的后端业务编排，供 fep-app-server 调用。
 * APP 侧公共报文解析仍放在 fep-app-server，key-server 只处理 bizData 中的业务参数。</p>
 */
@RestController
public class KeyController {
    private static final Logger log = LoggerFactory.getLogger(KeyController.class);

    private final KeySyncService keySyncService;

    public KeyController(KeySyncService keySyncService) {
        this.keySyncService = keySyncService;
    }

    /**
     * IF8A-02 请求同步密钥。
     *
     * <p>根据用户卡号和第三方用户ID生成 APP 侧可用的用户 SM2 密钥信息。
     * 该接口会查询本服务维护的 CA 密钥仓库，并调用 acc-security-server 完成用户密钥生成、
     * 用户公钥签名和用户私钥导出。</p>
     *
     * @param request 请求同步密钥业务参数
     * @return 请求同步密钥应答
     */
    @PostMapping("/requestKeyList")
    public RequestKeyListResult requestKeyList(@RequestBody RequestKeyListReqDTO request) {
        log.info("接收到IF8A-02请求同步密钥, thirdUserId={}, cardId={}, cardType={}",
                request == null ? null : request.getThirdUserId(),
                request == null ? null : request.getCardId(),
                request == null ? null : request.getCardType());
        return keySyncService.requestKeyList(request);
    }
}
