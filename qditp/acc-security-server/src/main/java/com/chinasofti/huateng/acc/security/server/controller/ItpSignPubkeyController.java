package com.chinasofti.huateng.acc.security.server.controller;

import com.chinasofti.huateng.acc.security.feign.domain.param.RequestSignInsDataParam;
import com.chinasofti.huateng.acc.security.feign.domain.param.RequestSignPubkeyParam;
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
public class ItpSignPubkeyController {

    private final ItpService itpService;
    private final ItpRequestSignVerifier itpRequestSignVerifier;

    public ItpSignPubkeyController(ItpService itpService,
                                   ItpRequestSignVerifier itpRequestSignVerifier) {
        this.itpService = itpService;
        this.itpRequestSignVerifier = itpRequestSignVerifier;
    }


    @PostMapping("/requestSignPubkey")
    public ResultVO requestSignPubkey(@RequestBody RequestSignPubkeyParam param) {

        Map<String, String> map =new HashMap<>();
        map.put("publicKeyX",param.getPublicKeyX());
        map.put("userId",param.getUserId());
        map.put("publicKeyEffectiveDate",param.getPublicKeyEffectiveDate());
        map.put("caPrivateKey",param.getCaPrivateKey());
        map.put("caPublicKey",param.getCaPublicKey());
        map.put("caSm2KeyPair",param.getCaSm2KeyPair());

        return itpService.requestSignPubkey(map);
    }


    @PostMapping("/requestSignInsData")
    public ResultVO requestSignInsData(@RequestBody RequestSignInsDataParam param) {

        Map<String, String> map =new HashMap<>();
        map.put("industryData",param.getIndustryData());
        map.put("logicNum",param.getLogicNum());
        return itpService.requestSignInsData(map);
    }


}
