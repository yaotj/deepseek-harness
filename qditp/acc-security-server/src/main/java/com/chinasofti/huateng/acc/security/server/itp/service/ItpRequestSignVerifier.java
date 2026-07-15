package com.chinasofti.huateng.acc.security.server.itp.service;

import com.chinasofti.huateng.acc.security.server.itp.util.ItpConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@Component
public class ItpRequestSignVerifier {

    @Value("${itp.signKey:bc4f7c96259acf9946094fa}")
    private String signKey;

    public boolean checkSign(HttpServletRequest request) {
        String signType = request.getParameter(ItpConstants.SIGN_TYPE);
        if (StringUtils.isBlank(signType)) {
            return false;
        }
        if (ItpConstants.NO_SIGN.equals(signType)) {
            return true;
        }
        String requestSign = request.getParameter(ItpConstants.SIGN);
        String checkSign = switch (signType) {
            case ItpConstants.SHA1_SIGN -> DigestUtils.sha1Hex(buildSignSource(request));
            case ItpConstants.MD5_SIGN -> DigestUtils.md5Hex(buildSignSource(request));
            default -> null;
        };
        return StringUtils.isNotBlank(requestSign) && StringUtils.equalsIgnoreCase(requestSign, checkSign);
    }

    private String buildSignSource(HttpServletRequest request) {
        Enumeration<String> parameterNames = request.getParameterNames();
        List<String> parameterList = new ArrayList<>();
        while (parameterNames.hasMoreElements()) {
            parameterList.add(parameterNames.nextElement());
        }
        parameterList.remove(ItpConstants.SIGN);
        Collections.sort(parameterList);
        StringBuilder signSource = new StringBuilder();
        for (String key : parameterList) {
            String value = request.getParameter(key);
            if (StringUtils.isBlank(value)) {
                continue;
            }
            signSource.append(key).append("=").append(value).append("&");
        }
        signSource.append("key=").append(signKey);
        return signSource.toString();
    }
}
