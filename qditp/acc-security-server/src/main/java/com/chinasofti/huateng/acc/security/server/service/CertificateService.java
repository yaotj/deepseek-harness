package com.chinasofti.huateng.acc.security.server.service;

import com.chinasofti.huateng.acc.security.feign.domain.cardCertificate.CardCertificateParam;
import com.chinasofti.huateng.acc.security.feign.domain.cardCertificate.CardCertificateResult;
import com.chinasofti.huateng.common.response.ResultVO;


public interface CertificateService {


    ResultVO<CardCertificateResult> getCertificate(CardCertificateParam param) throws InterruptedException;


}
