package com.chinasofti.huateng.acc.security.server.controller;

import com.alibaba.fastjson.JSON;
import com.chinasofti.huateng.acc.security.feign.domain.param.*;
import com.chinasofti.huateng.acc.security.server.itp.service.ItpService;
import com.chinasofti.huateng.acc.security.server.itp.service.ItpRequestSignVerifier;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * ITP剩余接口控制器。
 * 这里承接 secret-web 中除签名公钥、签名行业数据以外的其他 ITP 接口。
 */
@RestController
@RequestMapping("/ci/itp")
public class ItpRemainingController {
    private final ItpService itpService;
    private final ItpRequestSignVerifier itpRequestSignVerifier;

    public ItpRemainingController(ItpService itpService,
                                  ItpRequestSignVerifier itpRequestSignVerifier) {
        this.itpService = itpService;
        this.itpRequestSignVerifier = itpRequestSignVerifier;
    }

    /**
     * 请求生成地铁 CA 密钥。
     */
    @PostMapping("/requestCaKey")
    public ResultVO requestCaKey(@RequestBody RequestCaKeyParam param) {
        Map<String, String> map =new HashMap<>();
        map.put("keyIdx",param.getKeyIdx());
        return itpService.requestCaKey(map);
    }

    /**
     * 请求生成用户 SM2 密钥对。
     */
    @PostMapping("/requestUserSm2Key")
    public ResultVO requestUserSm2Key(@RequestBody RequestUserSm2KeyParam param) {

        Map<String, String> map =new HashMap<>();
        map.put("logicNum",param.getLogicNum());

        return itpService.requestUserSm2Key(map);
    }

    /**
     * 按 ITP 与 ACC 约定的 KEK 导出用户私钥。
     */
    @PostMapping("/requestExportUserPriKey")
    public ResultVO requestExportUserPriKey(@RequestBody RequestExportUserPriKeyParam param) {

        Map<String, String> map =new HashMap<>();
        map.put("privateKey",param.getPrivateKey());
        map.put("publicKey",param.getPublicKey());
        map.put("kekIdx",param.getKekIdx());

        return itpService.requestExportUserPriKey(map);
    }

    /**
     * 导出应用子密钥 DPK。  请求HCE卡片消费密钥
     */
    @PostMapping("/requestDPK")
    public ResultVO requestDPK(@RequestBody RequestDPKParam param) {

        Map<String, String> map =new HashMap<>();
        map.put("logicNum",param.getLogicNum());

        return itpService.requestDPK(map);
    }

    /**
     * 请求发售 HCE 卡数据。
     */
    @PostMapping("/requestHceCardData")
    public ResultVO requestHceCardData(@RequestBody RequestHceCardDataParam param) {

        Map<String, String> map =new HashMap<>();
        map.put("ticketCard",param.getTicketCard());
        map.put("iptUserId",param.getIptUserId());

        return itpService.requestHceCardData(map);
    }

}
