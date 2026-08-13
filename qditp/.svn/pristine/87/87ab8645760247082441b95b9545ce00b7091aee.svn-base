package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.AccountAppService;
import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;
import com.chinasofti.huateng.model.app.RequestKeyListReqDTO;
import com.chinasofti.huateng.model.app.RequestKeyListResult;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 账户及密钥类接口入口。
 *
 * <p>本层只负责解析 APP FormData 公共报文并转发业务 DTO；账户、支付通道和密钥业务
 * 分别由 account-server、key-server 维护。</p>
 */
@RestController
@RequestMapping("/ci/app")
public class AppAccountController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppAccountController.class);

    private final AccountAppService accountAppService;

    public AppAccountController(AccountAppService accountAppService) {
        this.accountAppService = accountAppService;
    }

    /**
     * IF8A-01 请求开户。
     *
     * @param request APP 公共 FormData 请求，bizData 为 {@link RequestApplicationReqDTO} JSON
     * @return 开户受理结果
     */
    @PostMapping("/requestApplication")
    public RequestApplicationResult requestApplication(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-01 请求开户，请求参数：{}", request);
        return accountAppService.requestApplication(parseBizData(request, RequestApplicationReqDTO.class));
    }

    /**
     * IF8A-02 请求同步密钥。
     *
     * <p>用户私钥密文、用户公钥及 CA 签名等数据由 key-server 查询和组装。</p>
     *
     * @param request APP 公共 FormData 请求，bizData 为 {@link RequestKeyListReqDTO} JSON
     * @return 密钥列表结果
     */
    @PostMapping("/requestKeyList")
    public RequestKeyListResult requestKeyList(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-02 请求同步密钥,请求参数: {}", request);
        return accountAppService.requestKeyList(parseBizData(request, RequestKeyListReqDTO.class));
    }

    /**
     * IF8A-23 请求添加支付通道。
     *
     * @param request APP 公共 FormData 请求，bizData 为 {@link RequestAddPayChannelReqDTO} JSON
     * @return 添加支付通道结果
     */
    @PostMapping("/requestAddPayChannel")
    public RequestAddPayChannelResult requestAddPayChannel(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-23 请求添加支付通道, 请求参数: {}", request);
        return accountAppService.requestAddPayChannel(parseBizData(request, RequestAddPayChannelReqDTO.class));
    }

    /**
     * IF8A-24 请求设置默认支付通道。
     *
     * @param request APP 公共 FormData 请求，bizData 为 {@link RequestSetDefaultPayChannelReqDTO} JSON
     * @return 设置默认支付通道结果
     */
    @PostMapping("/requestSetDefaultPayChannel")
    public RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-24 请求设置默认支付通道, 请求参数: {}", request);
        return accountAppService.requestSetDefaultPayChannel(parseBizData(request, RequestSetDefaultPayChannelReqDTO.class));
    }
}
