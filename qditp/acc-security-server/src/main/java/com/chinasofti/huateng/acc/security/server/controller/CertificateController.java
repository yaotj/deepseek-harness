package com.chinasofti.huateng.acc.security.server.controller;

import com.chinasofti.huateng.acc.security.feign.domain.cardCertificate.CardCertificateParam;
import com.chinasofti.huateng.acc.security.feign.domain.cardCertificate.CardCertificateResult;
import com.chinasofti.huateng.acc.security.server.service.CertificateService;
import com.chinasofti.huateng.common.response.ResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;


@Validated
@RestController
public class CertificateController {

    @Autowired
    private CertificateService certificateService;


    @PostMapping("get/card/certificate")
    public ResultVO<CardCertificateResult> getPublicKey(@RequestBody @Valid CardCertificateParam parm) throws InterruptedException {
        return certificateService.getCertificate(parm);
    }

}

