package com.chinasofti.huateng.micro.web.utils;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.codec.Base64Decoder;
import org.jose4j.json.JsonUtil;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;

public class JwtUtils {

    public static Logger log = LoggerFactory.getLogger(JwtUtils.class);

    public static PublicKey publicKey;

    public static PrivateKey privateKey;

    public static PublicKey readPublicKeyFromPemFile(String fileName) throws Exception {
        String publicKeyPEM = null;
        Resource resource = ResourceUtils.getLocalFileResource(fileName);
        if (!resource.exists()) {
            throw new Exception("file not find:" + resource.getFilename());
        }
        try (
                InputStream fileInputStream = resource.getInputStream()
        ) {
            byte[] buffer = new byte[fileInputStream.available()];
            int a = fileInputStream.read(buffer);
            publicKeyPEM = new String(buffer).replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "").replace("\r\n", "");
        } catch (Exception e) {
            log.error("{}", e.getMessage(), e);
        }
        return getPublicKey(publicKeyPEM);
    }

    public static PrivateKey readPrivateFromFile(String fileName) throws Exception {
        String privateKey = null;
        Resource resource = ResourceUtils.getLocalFileResource(fileName);
        if (!resource.exists()) {
            throw new Exception("file not find:" + resource.getFilename());
        }

        try (
                InputStream fileInputStream = resource.getInputStream()
        ) {
            byte[] buffer = new byte[fileInputStream.available()];
            int a = fileInputStream.read(buffer);
            privateKey = new String(buffer).replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replace("\r\n", "");
        } catch (Exception e) {
            log.error("{}", e.getMessage(), e);
        }
        return getPrivateKey(privateKey);
    }


    public static PrivateKey getPrivateKey(String privateKey) throws Exception {
        byte[] decoded = Base64Decoder.decode(privateKey);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    public static PublicKey getPublicKey(String publicKeyPEM) throws Exception {
        byte[] decoded = Base64Decoder.decode(publicKeyPEM);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    public static String createToken(PrivateKey privateKey, JwtClaims jwtClaims) {
        if (jwtClaims == null) {
            return null;
        }
        JsonWebSignature jws = new JsonWebSignature();
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setKey(privateKey);
        jws.setPayload(jwtClaims.toJson());
        String token = null;
        try {
            token = jws.getCompactSerialization();
        } catch (Exception e) {
            log.error("{}", e.getMessage(), e);
        }
        return token;
    }

    public static Map<String, Object> getExtendData() {
        Map<String, Object> extendData = null;
        String authorization = MDC.get("authorization");
        if (authorization != null && !authorization.isEmpty()) {
            try {
                String play = authorization.split("\\.")[1];
                extendData = JsonUtil.parseJson(new String(Base64.decode(play.getBytes(StandardCharsets.UTF_8))));
            } catch (Exception e) {
                log.error("{}", e.getMessage(), e);
            }
        }
        return extendData;
    }

    public static Map<String, Object> getExtendData(String token) {
        Map<String, Object> extendData = null;
        try {
            String play = token.split("\\.")[1];
            extendData = JsonUtil.parseJson(new String(Base64.decode(play.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            log.error("{}", e.getMessage(), e);
        }
        return extendData;
    }

    public static String getAuthorizationDataByMdc(String name) {
        String authorization = MDC.get("authorization");
        if (authorization != null && !authorization.isEmpty()) {
            Map<String, Object> extendData = getExtendData(authorization);
            if (extendData == null) {
                return null;
            }
            if (extendData.get(name) == null) {
                return null;
            }
            return extendData.get(name).toString();
        }
        return null;
    }


    public static JwtClaims checkToken(String token, PublicKey publicKey) throws Exception {
        token = token.replace("\n", "")
                .replace("\r", "")
                .replace("Bearer", "")
                .replace("bearer", "")
                .replace(" ", "");
        Map<String, Object> extendData = getExtendData(token);
        if (extendData == null) {
            return null;
        }
        if (extendData.get("iss") == null || extendData.get("aud") == null) {
            return null;
        }

        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setVerificationKey(publicKey)
                .setExpectedIssuer(false, extendData.get("iss").toString())
                .setExpectedAudience(false, extendData.get("aud").toString())
                .build();
        return jwtConsumer.processToClaims(token);
    }

    public static JwtClaims createJwtClaims(String issuer, String audience, Long timeMinutes
            , Map<String, Object> extendData) {
        JwtClaims claims = new JwtClaims();
        claims.setGeneratedJwtId();
        claims.setIssuedAtToNow();
        claims.setIssuer(issuer);
        claims.setAudience(audience);
        claims.setExpirationTimeMinutesInTheFuture(timeMinutes);
        if (extendData == null || extendData.isEmpty()) {
            return null;
        }
        extendData.forEach(claims::setClaim);
        return claims;
    }
}
