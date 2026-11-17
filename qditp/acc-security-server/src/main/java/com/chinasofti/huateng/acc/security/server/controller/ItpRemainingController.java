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


    @PostMapping("/requestCaKey")
    public ResultVO requestCaKey(@RequestBody RequestCaKeyParam param) {
        Map<String, String> map =new HashMap<>();
        map.put("keyIdx",param.getKeyIdx());
        return itpService.requestCaKey(map);
    }


    @PostMapping("/requestUserSm2Key")
    public ResultVO requestUserSm2Key(@RequestBody RequestUserSm2KeyParam param) {

        Map<String, String> map =new HashMap<>();
        map.put("logicNum",param.getLogicNum());

        return itpService.requestUserSm2Key(map);
    }


    @PostMapping("/requestExportUserPriKey")
    public ResultVO requestExportUserPriKey(@RequestBody RequestExportUserPriKeyParam param) {

        Map<String, String> map =new HashMap<>();
        map.put("privateKey",param.getPrivateKey());
        map.put("publicKey",param.getPublicKey());
        map.put("kekIdx",param.getKekIdx());

        return itpService.requestExportUserPriKey(map);
    }


    @PostMapping("/requestDPK")
    public ResultVO requestDPK(@RequestBody RequestDPKParam param) {

        Map<String, String> map =new HashMap<>();
        map.put("logicNum",param.getLogicNum());

        return itpService.requestDPK(map);
    }


    @PostMapping("/requestHceCardData")
    public ResultVO requestHceCardData(@RequestBody RequestHceCardDataParam param) {

        Map<String, String> map =new HashMap<>();
        map.put("ticketCard",param.getTicketCard());
        map.put("iptUserId",param.getIptUserId());

        return itpService.requestHceCardData(map);
    }

}
