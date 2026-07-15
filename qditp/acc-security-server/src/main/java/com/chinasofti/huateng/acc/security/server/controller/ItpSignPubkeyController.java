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

    /**
     * 请求签名公钥
     * @param param
     * @return
     */
    @PostMapping("/requestSignPubkey")
    public ResultVO requestSignPubkey(@RequestBody RequestSignPubkeyParam param) {
//        if (!itpRequestSignVerifier.checkSign(request)) {
//            return ResultMapper.error( "签名校验未通过");
//        }

        Map<String, String> map =new HashMap<>();
        map.put("publicKeyX",param.getPublicKeyX());
        map.put("userId",param.getUserId());
        map.put("publicKeyEffectiveDate",param.getPublicKeyEffectiveDate());
        map.put("caPrivateKey",param.getCaPrivateKey());
        map.put("caPublicKey",param.getCaPublicKey());
        map.put("caSm2KeyPair",param.getCaSm2KeyPair());

        return itpService.requestSignPubkey(map);
    }

    /**
     * 请求签名行业数据
     * @param param
     * @return
     */
    @PostMapping("/requestSignInsData")
//    public ResultVO requestSignInsData(HttpServletRequest request) {
    public ResultVO requestSignInsData(@RequestBody RequestSignInsDataParam param) {
//        if (!itpRequestSignVerifier.checkSign(request)) {
//            return ResultMapper.error("签名校验未通过");
//        }

        Map<String, String> map =new HashMap<>();
        map.put("industryData",param.getIndustryData());
        map.put("logicNum",param.getLogicNum());
        return itpService.requestSignInsData(map);
    }


//    @SuppressWarnings("unchecked")
//    private Map<String, String> parseBizData(HttpServletRequest request) {
//        return JSON.parseObject(request.getParameter("bizData"), Map.class);
//    }
}
