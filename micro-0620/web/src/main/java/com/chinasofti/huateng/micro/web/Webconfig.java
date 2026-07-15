package com.chinasofti.huateng.micro.web;

import com.chinasofti.huateng.micro.web.utils.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "other.web")
public class Webconfig {

    public static Logger log = LoggerFactory.getLogger(Webconfig.class);

    boolean enableLogRequestInFilter = false;

    boolean enableLogTakeTimesInInterceptor = true;

    long slowRequestOfMillis = 2000;

    boolean enableBodyCacheFilter = true;

    int logResponseMaxSize = 20000;

    String logBodyCacheUrls = "/**";

    String forbiddenUrls = "/";

    String skipCheckTokenUrls = "/**";

    String tokenPrivateKeyPath = "";

    String tokenPublicKeyPath = "";

    String tokenPublicKey = "";

    String unSafeUrlChars = "";

    String actuatorAllowIp = "";

    String removeHeaders = "";

    List<String> unSafeUrlCharsList;

    List<String> actuatorAllowIpList;

    List<String> removeHeadersList;

    boolean readInputStreamInMultipartReq = false;

    public void setRemoveHeaders(String removeHeaders) {
        this.removeHeaders = removeHeaders;
        this.removeHeadersList = Arrays.stream(removeHeaders.split(",")).toList();
    }

    public String getRemoveHeaders() {
        return removeHeaders;
    }

    public List<String> getRemoveHeadersList() {
        return removeHeadersList;
    }

    public String getActuatorAllowIp() {
        return actuatorAllowIp;
    }

    public List<String> getActuatorAllowIpList() {
        return actuatorAllowIpList;
    }

    public void setActuatorAllowIp(String actuatorAllowIp) {
        this.actuatorAllowIp = actuatorAllowIp;
        this.actuatorAllowIpList = Arrays.stream(actuatorAllowIp.split(",")).toList();
    }

    public String getUnSafeUrlChars() {
        return unSafeUrlChars;
    }

    public List<String> getUnSafeUrlCharsList() {
        return unSafeUrlCharsList;
    }

    public void setUnSafeUrlChars(String unSafeUrlChars) {
        this.unSafeUrlChars = unSafeUrlChars;
        this.unSafeUrlCharsList = Arrays.stream(unSafeUrlChars.split(" ")).toList();
    }

    public String getForbiddenUrls() {
        return forbiddenUrls;
    }

    public void setForbiddenUrls(String forbiddenUrls) {
        this.forbiddenUrls = forbiddenUrls;
    }

    public boolean isEnableLogRequestInFilter() {
        return enableLogRequestInFilter;
    }

    public void setEnableLogRequestInFilter(boolean enableLogRequestInFilter) {
        this.enableLogRequestInFilter = enableLogRequestInFilter;
    }

    public boolean isEnableLogTakeTimesInInterceptor() {
        return enableLogTakeTimesInInterceptor;
    }

    public void setEnableLogTakeTimesInInterceptor(boolean enableLogTakeTimesInInterceptor) {
        this.enableLogTakeTimesInInterceptor = enableLogTakeTimesInInterceptor;
    }

    public long getSlowRequestOfMillis() {
        return slowRequestOfMillis;
    }

    public void setSlowRequestOfMillis(long slowRequestOfMillis) {
        this.slowRequestOfMillis = slowRequestOfMillis;
    }

    public boolean isEnableBodyCacheFilter() {
        return enableBodyCacheFilter;
    }

    public void setEnableBodyCacheFilter(boolean enableBodyCacheFilter) {
        this.enableBodyCacheFilter = enableBodyCacheFilter;
    }

    public int getLogResponseMaxSize() {
        return logResponseMaxSize;
    }

    public void setLogResponseMaxSize(int logResponseMaxSize) {
        this.logResponseMaxSize = logResponseMaxSize;
    }

    public String getLogBodyCacheUrls() {
        return logBodyCacheUrls;
    }

    public void setLogBodyCacheUrls(String logBodyCacheUrls) {
        this.logBodyCacheUrls = logBodyCacheUrls;
    }

    public String getSkipCheckTokenUrls() {
        return skipCheckTokenUrls;
    }

    public void setSkipCheckTokenUrls(String skipCheckTokenUrls) {
        this.skipCheckTokenUrls = skipCheckTokenUrls;
    }

    public String getTokenPublicKeyPath() {
        return tokenPublicKeyPath;
    }

    public boolean isReadInputStreamInMultipartReq() {
        return readInputStreamInMultipartReq;
    }

    public void setReadInputStreamInMultipartReq(boolean readInputStreamInMultipartReq) {
        this.readInputStreamInMultipartReq = readInputStreamInMultipartReq;
    }

    public void setTokenPublicKeyPath(String tokenPublicKeyPath) {
        this.tokenPublicKeyPath = tokenPublicKeyPath;
        if (tokenPublicKeyPath.length() > 0) {
            try {
                JwtUtils.publicKey = JwtUtils.readPublicKeyFromPemFile(tokenPublicKeyPath);
            } catch (Exception e) {
                log.error("{}", e.getMessage(), e);
            }
        }
    }

    public String getTokenPrivateKeyPath() {
        return tokenPrivateKeyPath;
    }

    public void setTokenPrivateKeyPath(String tokenPrivateKeyPath) {
        this.tokenPrivateKeyPath = tokenPrivateKeyPath;
        if (tokenPrivateKeyPath.length() > 0) {
            try {
                JwtUtils.privateKey = JwtUtils.readPrivateFromFile(tokenPrivateKeyPath);
            } catch (Exception e) {
                log.error("{}", e.getMessage(), e);
            }
        }
    }

    public String getTokenPublicKey() {
        return tokenPublicKey;
    }

    public void setTokenPublicKey(String tokenPublicKey) {
        this.tokenPublicKey = tokenPublicKey;
        if (tokenPublicKey.length() > 0) {
            try {
                JwtUtils.publicKey = JwtUtils.getPublicKey(tokenPublicKey);
            } catch (Exception e) {
                log.error("{}", e.getMessage(), e);
            }
        }
    }
}
