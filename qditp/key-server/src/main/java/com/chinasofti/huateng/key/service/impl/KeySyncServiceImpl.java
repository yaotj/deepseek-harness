package com.chinasofti.huateng.key.service.impl;

import com.chinasofti.huateng.key.entity.MetroCaKeystore;
import com.chinasofti.huateng.key.mapper.MetroCaKeystoreMapper;
import com.chinasofti.huateng.key.constant.KeyErrorCodeEnum;
import com.chinasofti.huateng.key.service.KeySyncService;
import com.chinasofti.huateng.key.util.TripleDesEcbUtils;
import com.chinasofti.huateng.model.app.KeyItemDTO;
import com.chinasofti.huateng.model.app.RequestKeyListReqDTO;
import com.chinasofti.huateng.model.app.RequestKeyListResult;
import com.chinasofti.huateng.model.security.RequestExportUserPriKeyReqDTO;
import com.chinasofti.huateng.model.security.RequestExportUserPriKeyRespDTO;
import com.chinasofti.huateng.model.security.RequestSignPubkeyReqDTO;
import com.chinasofti.huateng.model.security.RequestSignPubkeyRespDTO;
import com.chinasofti.huateng.model.security.RequestUserSm2KeyReqDTO;
import com.chinasofti.huateng.model.security.RequestUserSm2KeyRespDTO;
import com.chinasofti.huateng.rpc.security.SecurityClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * IF8A-02 请求同步密钥业务实现。
 *
 * <p>处理步骤与旧系统保持一致：查询可用 CA 密钥，随机选择一条 CA，
 * 调用 acc-security-server 生成用户 SM2 密钥对，使用 CA 签名用户公钥，
 * 导出用户私钥后进行 3DES 转加密，最后组装 keyList 返回 APP。</p>
 */
@Service
public class KeySyncServiceImpl implements KeySyncService {
    private static final Logger log = LoggerFactory.getLogger(KeySyncServiceImpl.class);
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2000, 1, 1, 0, 0, 0);

    private final MetroCaKeystoreMapper metroCaKeystoreMapper;
    private final SecurityClient securityClient;

    @Value("${key.sync.days:7}")
    private int keySyncDays;

    @Value("${key.sync.kek-idx:0012}")
    private String kekIdx;

    @Value("${acc.3des.key}")
    private String acc3DesKey;

    @Value("${appserver.3des.key}")
    private String appserver3DesKey;

    public KeySyncServiceImpl(MetroCaKeystoreMapper metroCaKeystoreMapper,
                              SecurityClient securityClient) {
        this.metroCaKeystoreMapper = metroCaKeystoreMapper;
        this.securityClient = securityClient;
    }

    /**
     * 生成用户同步密钥列表。
     *
     * @param request 请求同步密钥业务参数
     * @return 请求同步密钥应答
     */
    @Override
    public RequestKeyListResult requestKeyList(RequestKeyListReqDTO request) {
        RequestKeyListResult result = new RequestKeyListResult();
        result.setSignType("01");
        result.setSign("");

        String validMsg = validateRequest(request);
        if (validMsg != null) {
            result.setRetCode(KeyErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg(validMsg);
            return result;
        }

        try {
            String thirdUserId = request.getThirdUserId().trim();
            String cardId = request.getCardId().trim();
            String hexUserId = toEightHex(thirdUserId);
            String hexEffectiveDate = buildHexEffectiveDate();

            MetroCaKeystore caKeystore = selectRandomCaKeystore();
            if (caKeystore == null) {
                result.setRetCode(KeyErrorCodeEnum.NO_CA_KEYSTORE.getCode());
                result.setRetMsg(KeyErrorCodeEnum.NO_CA_KEYSTORE.getMsg());
                return result;
            }
            RequestUserSm2KeyRespDTO userSm2Key = requestUserSm2Key(cardId);
            String smPublicKey = userSm2Key.getPublicKey();
            String smPrivateKey = userSm2Key.getPrivateKey();
            String publicKeyX = extractPublicKeyX(smPublicKey);

            RequestSignPubkeyRespDTO signPubkey = requestSignPubkey(caKeystore, hexEffectiveDate, hexUserId, publicKeyX);
            RequestExportUserPriKeyRespDTO exportPriKey = requestExportUserPriKey(smPrivateKey, smPublicKey);
            String appEncryptedPrivateKey = transferPrivateKeyForApp(exportPriKey.getUserPrivateKeyByKes());

            result.setRetCode(KeyErrorCodeEnum.SUCCESS.getCode());
            result.setRetMsg(KeyErrorCodeEnum.SUCCESS.getMsg());
            result.setKeyList(Collections.singletonList(buildKeyItem(
                    hexUserId,
                    appEncryptedPrivateKey,
                    normalizePublicKey(smPublicKey),
                    hexEffectiveDate,
                    signPubkey.getSignData(),
                    caKeystore.getKeyIdx())));
            return result;
        } catch (Exception e) {
            log.error("处理IF8A-02请求同步密钥异常, thirdUserId={}, cardId={}",
                    request == null ? null : request.getThirdUserId(),
                    request == null ? null : request.getCardId(), e);
            result.setRetCode(KeyErrorCodeEnum.FAIL.getCode());
            result.setRetMsg(KeyErrorCodeEnum.FAIL.getMsg());
            return result;
        }
    }

    /**
     * 校验请求同步密钥的必填业务参数。
     *
     * @param request 请求同步密钥业务参数
     * @return 校验失败原因，返回null表示校验通过
     */
    private String validateRequest(RequestKeyListReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        return null;
    }

    /**
     * 从 CA 密钥仓库中随机选择一条启用状态的 CA 密钥。
     *
     * @return 可用CA密钥
     */
    private MetroCaKeystore selectRandomCaKeystore() {
        List<MetroCaKeystore> caKeystores = metroCaKeystoreMapper.selectActiveList();
        if (caKeystores == null || caKeystores.isEmpty()) {
            log.error("无可用CA证书");
            return null;
        }
        return caKeystores.get(ThreadLocalRandom.current().nextInt(caKeystores.size()));
    }

    /**
     * 调用 acc-security-server 生成用户 SM2 密钥对。
     *
     * @param cardId 地铁会员卡号
     * @return 用户SM2密钥对
     */
    private RequestUserSm2KeyRespDTO requestUserSm2Key(String cardId) {
        RequestUserSm2KeyReqDTO request = new RequestUserSm2KeyReqDTO();
        request.setLogicNum(cardId + cardId);
        RequestUserSm2KeyRespDTO response = securityClient.requestUserSm2Key(request);
        if (!isSecuritySuccess(response) || !StringUtils.hasText(response.getPrivateKey())
                || !StringUtils.hasText(response.getPublicKey())) {
            throw new IllegalStateException("生成用户SM2密钥失败");
        }
        return response;
    }

    /**
     * 调用 acc-security-server 使用 CA 签名用户公钥。
     *
     * @param caKeystore CA密钥
     * @param hexEffectiveDate 公钥有效期HEX
     * @param hexUserId 用户ID HEX
     * @param publicKeyX 用户公钥X分量
     * @return 用户公钥签名结果
     */
    private RequestSignPubkeyRespDTO requestSignPubkey(MetroCaKeystore caKeystore,
                                                       String hexEffectiveDate,
                                                       String hexUserId,
                                                       String publicKeyX) {
        RequestSignPubkeyReqDTO request = new RequestSignPubkeyReqDTO();
        request.setCaPrivateKey(caKeystore.getKeyPrivate());
        request.setCaPublicKey(caKeystore.getKeyPublic());
        request.setCaSm2KeyPair(caKeystore.getKeyPair());
        request.setPublicKeyEffectiveDate(hexEffectiveDate);
        request.setPublicKeyX(publicKeyX);
        request.setUserId(hexUserId);
        RequestSignPubkeyRespDTO response = securityClient.requestSignPubkey(request);
        if (!isSecuritySuccess(response) || !StringUtils.hasText(response.getSignData())) {
            throw new IllegalStateException("签名用户公钥失败");
        }
        return response;
    }

    /**
     * 调用 acc-security-server 导出 KEK 加密的用户私钥。
     *
     * @param smPrivateKey LMK加密的用户私钥
     * @param smPublicKey 用户公钥
     * @return 导出的用户私钥
     */
    private RequestExportUserPriKeyRespDTO requestExportUserPriKey(String smPrivateKey, String smPublicKey) {
        RequestExportUserPriKeyReqDTO request = new RequestExportUserPriKeyReqDTO();
        request.setKekIdx(kekIdx);
        request.setPrivateKey(smPrivateKey);
        request.setPublicKey(smPublicKey);
        RequestExportUserPriKeyRespDTO response = securityClient.requestExportUserPriKey(request);
        if (!isSecuritySuccess(response) || !StringUtils.hasText(response.getUserPrivateKeyByKes())) {
            throw new IllegalStateException("导出用户私钥失败");
        }
        return response;
    }

    /**
     * 将 ACC 3DES 密钥加密的用户私钥转为 APP_SERVER 3DES 密钥加密。
     *
     * @param accEncryptedPrivateKey ACC侧3DES加密的用户私钥
     * @return APP_SERVER侧3DES加密的用户私钥
     */
    private String transferPrivateKeyForApp(String accEncryptedPrivateKey) {
        byte[] privateKey = TripleDesEcbUtils.decryptHex(accEncryptedPrivateKey, acc3DesKey);
        return TripleDesEcbUtils.encryptTextToBase64(bytesToHex(privateKey), appserver3DesKey);
    }

    /**
     * 组装 IF8A-02 返回的单条用户非对称密钥。
     *
     * @param hexUserId 用户ID HEX
     * @param keyPrivate APP侧加密用户私钥
     * @param keyPublic 用户公钥XY
     * @param hexEffectiveDate 用户公钥有效期HEX
     * @param signData CA签名用户公钥数据
     * @param caIdx CA密钥索引
     * @return 单条密钥信息
     */
    private KeyItemDTO buildKeyItem(String hexUserId,
                                    String keyPrivate,
                                    String keyPublic,
                                    String hexEffectiveDate,
                                    String signData,
                                    String caIdx) {
        KeyItemDTO keyItem = new KeyItemDTO();
        keyItem.setKeyId("01");
        keyItem.setKeyType("1");
        keyItem.setKeyUserId(hexUserId);
        keyItem.setKeyPrivate(keyPrivate);
        keyItem.setKeyPublic(keyPublic);
        keyItem.setKeyPublicEffectiveDate(hexEffectiveDate);
        keyItem.setSignData(signData);
        keyItem.setCaIdx(caIdx);
        return keyItem;
    }

    /**
     * 判断 acc-security-server 是否返回成功。
     *
     * @param response acc-security-server应答
     * @return true表示成功
     */
    private boolean isSecuritySuccess(com.chinasofti.huateng.model.security.SecurityBaseRespDTO response) {
        return response != null && ("200".equals(response.getRetCode()) || "0000".equals(response.getRetCode()));
    }

    /**
     * 生成用户公钥有效期HEX。
     *
     * <p>旧系统按当前时间加 key.sync.days 后转成 2000-01-01 以来秒数的四字节HEX。</p>
     *
     * @return 公钥有效期HEX
     */
    private String buildHexEffectiveDate() {
        LocalDateTime effectiveDate = LocalDateTime.now().plusDays(keySyncDays);
        long seconds = effectiveDate.toEpochSecond(ZoneOffset.ofHours(8))
                - BASE_TIME.toEpochSecond(ZoneOffset.ofHours(8));
        return String.format("%08X", seconds & 0xFFFFFFFFL);
    }

    /**
     * 将第三方用户ID转为四字节HEX字符串。
     *
     * @param thirdUserId 第三方用户ID
     * @return 四字节HEX字符串
     */
    private String toEightHex(String thirdUserId) {
        long userId = Long.parseLong(thirdUserId);
        return String.format("%08X", userId & 0xFFFFFFFFL);
    }

    /**
     * 从用户公钥中提取X分量。
     *
     * @param smPublicKey 用户公钥
     * @return 用户公钥X分量
     */
    private String extractPublicKeyX(String smPublicKey) {
        String publicKey = normalizePublicKey(smPublicKey);
        return publicKey.substring(0, 64);
    }

    /**
     * 标准化用户公钥为X+Y共128位HEX。
     *
     * @param smPublicKey acc-security-server返回的用户公钥
     * @return X+Y用户公钥
     */
    private String normalizePublicKey(String smPublicKey) {
        if (!StringUtils.hasText(smPublicKey) || smPublicKey.trim().length() < 128) {
            log.error("用户公钥格式错误, smPublicKey={}", smPublicKey);
            return null;
        }
        String publicKey = smPublicKey.trim().toUpperCase();
        return publicKey.substring(publicKey.length() - 128);
    }

    /**
     * 将字节数组转为大写HEX字符串。
     *
     * @param bytes 字节数组
     * @return HEX字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02X", value & 0xFF));
        }
        return builder.toString();
    }
}
