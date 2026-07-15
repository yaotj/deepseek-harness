package com.chinasofti.huateng.acc.security.server.service.impl;

import com.chinasofti.huateng.acc.security.feign.domain.cardCertificate.CardCertificateParam;
import com.chinasofti.huateng.acc.security.feign.domain.cardCertificate.CardCertificateResult;
import com.chinasofti.huateng.acc.security.server.config.Constant;
import com.chinasofti.huateng.acc.security.server.config.SecurityConfig;
import com.chinasofti.huateng.acc.security.server.service.CertificateService;
import com.chinasofti.huateng.acc.security.server.util.TransformUtils;
import com.chinasofti.huateng.acc.security.server.util.Util;
import com.chinasofti.huateng.acc.security.server.util.sm2.SM2;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.StringJoiner;

/**
 * @author houkepan
 * @date 2020/5/6 11:56
 */
@Service
public class CertificateServiceImpl implements CertificateService {
    private static final Logger log = LoggerFactory.getLogger(CertificateServiceImpl.class);

    @Autowired
    private SecurityConfig securityConfig;

    @Override
    public ResultVO<CardCertificateResult> getCertificate(CardCertificateParam param) {
        log.info("{}消息组装开始", Constant.Log.GET_CERTIFICATE);
        CardCertificateResult result = new CardCertificateResult();
        try {
            String publickey = TransformUtils.bytesToHex(Base64.getDecoder().decode(param.getPublickey()));
            log.info("公钥为" + publickey);

            StringJoiner joiner = new StringJoiner("")
                    .add(param.getCert_format())
                    .add(param.getOrg_id())
                    .add(param.getCert_expire_time())
                    .add(param.getCert_seq())
                    .add(param.getSign_algorithm())
                    .add(param.getEncrypt_algorithm())
                    .add(param.getParameter_id())
                    .add(param.getPublickey_length())
                    .add(publickey);

            log.info(Constant.Log.GET_CERTIFICATE + joiner);
            BeanUtils.copyProperties(param, result);

            result.setCert_index("01");
            String sign = getSign(String.valueOf(joiner));
            result.setCert_sign(Base64.getEncoder().encodeToString(Util.hexStringToBytes(sign)));

        } catch (Exception e) {
            log.error(e.getMessage());
            return ResultMapper.error("程序异常");
        }
        return ResultMapper.ok(result);
    }

    public String getSign(String str) {
        SM2 sm2 = new SM2();

        String privatekey = securityConfig.getPrivateKey();
        String publickey = securityConfig.getPublicKey();
        //截取私钥
        byte[] byteArraySm2PrivateKey = TransformUtils.HexStringToByteArr(privatekey);
        //截取公钥
        byte[] byteArraySm2PublicKey = TransformUtils.HexStringToByteArr(publickey);

        //用户ID，使用固定值1234567812345678。
        String strUserID = "1234567812345678";
        byte[] byteArrayUserID = strUserID.getBytes();

        //原文
        byte[] byteArrayOriginData = TransformUtils.HexStringToByteArr(str);
//        byte[] byteArrayOriginData = str.getBytes();
        //计算哈希值
        byte[] byteArrayPublicKeyX = new byte[32];
        byte[] byteArrayPublicKeyY = new byte[32];
        System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
        System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);
        byte[] byteArrayHash = sm2
                .getHash(byteArrayOriginData, byteArrayUserID, byteArrayPublicKeyX, byteArrayPublicKeyY);

        //签名
        byte[] byteArraySignData = sm2.sm2Sign(byteArrayHash, byteArraySm2PrivateKey);
        log.info("签名数据：" + str + " \n签名HexString=========>" + TransformUtils.bytesToHex(byteArraySignData));

        return TransformUtils.bytesToHex(byteArraySignData);
    }

}
