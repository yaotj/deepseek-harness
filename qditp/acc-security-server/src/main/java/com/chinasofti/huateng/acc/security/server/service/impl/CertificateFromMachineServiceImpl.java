//package com.chinasofti.huateng.acc.security.server.service.impl;
//
//import com.chinasofti.huateng.acc.security.feign.domain.cardCertificate.CardCertificateParam;
//import com.chinasofti.huateng.acc.security.feign.domain.cardCertificate.CardCertificateResult;
//import com.chinasofti.huateng.acc.security.server.config.Constant;
//import com.chinasofti.huateng.acc.security.server.config.LocalCache;
//import com.chinasofti.huateng.acc.security.server.config.SecurityConfig;
//import com.chinasofti.huateng.acc.security.server.param.CertificateBean;
//import com.chinasofti.huateng.acc.security.server.service.CertificateService;
//import com.chinasofti.huateng.acc.security.server.socket.ClientSendMsg;
//import com.chinasofti.huateng.acc.security.server.util.TransformUtils;
//import com.chinasofti.huateng.acc.security.server.util.Util;
//import com.chinasofti.huateng.acc.security.server.util.sm2.SM2EncDecUtils;
//import com.chinasofti.huateng.acc.security.server.util.sm2.SM2KeyVO;
//import com.chinasofti.huateng.acc.security.server.util.sm2.SM2SignVO;
//import com.chinasofti.huateng.acc.security.server.util.sm2.SM2SignVerUtils;
//import com.chinasofti.huateng.common.response.ResultMapper;
//import com.chinasofti.huateng.common.response.ResultVO;
//import com.chinasofti.huateng.common.util.ByteConvertUtil;
//import io.netty.channel.Channel;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.BeanUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.util.Base64Utils;
//
//import java.util.StringJoiner;
//
///**
// * @author houkepan
// * @date 2020/5/6 11:56
// */
//@Service
//public class CertificateFromMachineServiceImpl implements CertificateService {
//    private static final Logger log = LoggerFactory.getLogger(CertificateFromMachineServiceImpl.class);
//
//    @Autowired
//    SecurityConfig securityConfig;
//    @Autowired
//    ClientSendMsg clientSendMsg;
//
//    @Override
//    public ResultVO<CardCertificateResult> getCertificate(CardCertificateParam param) throws InterruptedException {
//
//
//        //命令类型： D3
//        //命令：02
//        //算法标识：07
//        //秘钥长度：0100
//        //秘钥索引：0001
//        //秘钥口令：0000000000000000
//        CertificateBean certificateBean = new CertificateBean();
//        certificateBean.setSecretFlag(TransformUtils.HexStringToByteArr(securityConfig.getIndex() + "00"));
//        String userReation = LocalCache.getSequence();
//        certificateBean.setSecretPassword(TransformUtils.HexStringToByteArr(userReation));
//
//        byte[] sendData = ByteConvertUtil.byteMergerAll(
//                certificateBean.getOrderType(),
//                certificateBean.getOrder(),
//                certificateBean.getAlgorithmFlag(),
//                certificateBean.getSecretFlag(),
//                certificateBean.getSecretIndex(),
//                certificateBean.getSecretPassword()
//        );
//
//        Channel channel = clientSendMsg.sendCertificateMsg(sendData,param, userReation);
//        return getBooleanResultVO(userReation, channel);
//    }
//
//    private ResultVO<CardCertificateResult> getBooleanResultVO(String userReation,CardCertificateParam param, Channel channel) throws InterruptedException {
//        long start = System.currentTimeMillis();
//        long end;
//
//        String resultData = (String) LocalCache.get(channel.id() + userReation.toUpperCase());
//        while (resultData == null) {
//            end = System.currentTimeMillis();
//            resultData = (String) LocalCache.get(channel.id() + userReation.toUpperCase());
//            if ((int) (end - start) >= Constant.OverTime.TIME) {
//                log.error("加密机调用超时，用户保留域{}", userReation.toUpperCase());
//                return ResultMapper.error("加密机调用超时");
//            } else {
//                Thread.sleep(Constant.OverTime.POLL_TIME);
//            }
//        }
//
//
//        if ("41".equals(resultData.substring(0, 2))) {
//            ResultVO<CardCertificateResult> resultVO = new ResultVO();
//            resultVO.setData(getResult(param, resultData));
//            return resultVO;
//        } else {
//            return ResultMapper.error();
//        }
//    }
//
//    private CardCertificateResult getResult(CardCertificateParam param, String resultData){
//        CardCertificateResult result = new CardCertificateResult();
//        BeanUtils.copyProperties(param, result);
//        String publicKey = TransformUtils.bytesToHex(Base64Utils.decodeFromString(param.getPublickey()));
//
//        StringJoiner joiner = new StringJoiner("")
//                .add(result.getCert_format())
//                .add(result.getOrg_id())
//                .add(result.getCert_expire_time())
//                .add(result.getCert_seq())
//                .add(result.getSign_algorithm())
//                .add(result.getEncrypt_algorithm())
//                .add(result.getParameter_id())
//                .add(result.getPublickey_length())
//                .add(publicKey);
//
//        String sign = getSM2SignFromMachine(joiner.toString(), resultData);
//
//        // 0x24
//        result.setBegin_Identifier("36");
//        result.setCert_index(securityConfig.getIndex());
//        result.setCert_sign(Base64Utils.encodeToString(Util.hexStringToBytes(sign)));
//
//        return result;
//    }
//
//    private String getSM2SignFromMachine(String param, String resultData) {
//        log.info("--产生SM2秘钥--:");
//
//        // 私钥密文
//        String secret = resultData.substring(6, 223);
//        // 公钥明文X+Y
//
//
//        log.info("原文hex:" + param);
//
//
//        return "";
//    }
//
//    public String getSM2SignFromLocal(String param) throws Exception {
//        log.info("--产生SM2秘钥--:");
//        SM2KeyVO sm2KeyVO = SM2EncDecUtils.generateKeyPair();
//        log.info("公钥:" + sm2KeyVO.getPubHexInSoft());
//        log.info("私钥:" + sm2KeyVO.getPriHexInSoft());
//
//        log.info("原文hex:" + param);
//
//        SM2SignVO sign = SM2SignVerUtils.Sign2SM2(Util.hexToByte(sm2KeyVO.getPriHexInSoft()), Util.hexToByte(param));
//        log.info("加密机签名结果:" + sign.getSm2_signForHard());
//        log.info("签名R:" + sign.sign_r);
//        log.info("签名S:" + sign.sign_s);
//
//        return sign.getSm2_signForHard();
//    }
//
//}
