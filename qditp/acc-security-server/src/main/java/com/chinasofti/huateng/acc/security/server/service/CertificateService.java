package com.chinasofti.huateng.acc.security.server.service;

import com.chinasofti.huateng.acc.security.feign.domain.cardCertificate.CardCertificateParam;
import com.chinasofti.huateng.acc.security.feign.domain.cardCertificate.CardCertificateResult;
import com.chinasofti.huateng.common.response.ResultVO;

/**
 * @author houkepan
 * @date 2020/5/6 11:55
 */
public interface CertificateService {

    /**
     * 获取公钥
     */
    ResultVO<CardCertificateResult> getCertificate(CardCertificateParam param) throws InterruptedException;


}
