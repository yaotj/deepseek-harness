package com.chinasofti.huateng.micro.web.utils;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HttpRequestWrapper extends HttpServletRequestWrapper {

    public static Logger log = LoggerFactory.getLogger(HttpRequestWrapper.class);

    private final byte[] cachedBody;
    private final Map<String, String[]> parameterMap;
    private final String reqParams;
    private final String characterEncoding;
    private final boolean isMultipartRequest;
    private final boolean readInputStreamInMultipartReq;

    public HttpRequestWrapper(HttpServletRequest request, boolean readInputStreamInMultipartReq) {
        super(request);
        this.readInputStreamInMultipartReq = readInputStreamInMultipartReq;
        this.isMultipartRequest = isMultipartContent(request);
        this.characterEncoding = Optional.ofNullable(request.getCharacterEncoding()).orElse(StandardCharsets.UTF_8.name());

        if (this.isMultipartRequest && !this.readInputStreamInMultipartReq) {
            this.cachedBody = new byte[0];
            this.parameterMap = super.getParameterMap();
            this.reqParams = "multipart/form-data request, skip all custom processing";
        } else {
            this.cachedBody = cacheRequestBody(request);
            this.parameterMap = mergeAllParameters(request);
            this.reqParams = parseReqParamsStrForLog();
        }
    }

    private boolean isMultipartContent(HttpServletRequest request) {
        String contentType = Optional.ofNullable(request.getContentType()).orElse("");
        return contentType.toLowerCase().startsWith("multipart/form-data");
    }

    private byte[] cacheRequestBody(HttpServletRequest request) {
        try (InputStream is = request.getInputStream()) {
            return is.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private Map<String, String[]> mergeAllParameters(HttpServletRequest request) {
        Map<String, String[]> mergedMap = new HashMap<>(super.getParameterMap());
        String contentType = Optional.ofNullable(request.getContentType()).orElse("");
        if (contentType.startsWith("application/x-www-form-urlencoded") && cachedBody.length > 0) {
            try {
                String formData = new String(cachedBody, characterEncoding);
                if (!formData.isBlank()) {
                    parseFormParams(formData, mergedMap);
                }
            } catch (UnsupportedEncodingException ignored) {

            }
        }
        return Collections.unmodifiableMap(mergedMap);
    }

    private void parseFormParams(String formData, Map<String, String[]> mergedMap) {
        String[] params = formData.split("&");
        for (String param : params) {
            if (param.isBlank()) {
                continue;
            }
            String[] keyValue = param.split("=", 2);
            String key = decode(keyValue[0]);
            String value = keyValue.length == 2 ? decode(keyValue[1]) : "";

            if (mergedMap.containsKey(key)) {
                List<String> values = new ArrayList<>(Arrays.asList(mergedMap.get(key)));
                values.add(value);
                mergedMap.put(key, values.toArray(new String[0]));
            } else {
                mergedMap.put(key, new String[]{value});
            }
        }
    }

    private String decode(String str) {
        try {
            return URLDecoder.decode(str, characterEncoding);
        } catch (UnsupportedEncodingException e) {
            return str;
        }
    }

    private String parseReqParamsStrForLog() {
        String contentType = Optional.ofNullable(getContentType()).orElse("");
        if (contentType.contains("application/json")) {
            return new String(cachedBody, StandardCharsets.UTF_8);
        }

        Map<String, String> singleValueMap = new HashMap<>();
        parameterMap.forEach((k, v) -> singleValueMap.put(k, v != null && v.length > 0 ? v[0] : ""));
        return singleValueMap.toString();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (this.isMultipartRequest && !this.readInputStreamInMultipartReq) {
            return super.getInputStream();
        }

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }

            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {

            }

            @Override
            public void close() throws IOException {
                super.close();
                byteArrayInputStream.close();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (this.isMultipartRequest && !this.readInputStreamInMultipartReq) {
            return super.getReader();
        }
        return new BufferedReader(new InputStreamReader(getInputStream(), characterEncoding));
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return parameterMap;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        if (this.isMultipartRequest && !this.readInputStreamInMultipartReq) {
            return super.getParameterNames();
        }
        return Collections.enumeration(parameterMap.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        if (this.isMultipartRequest && !this.readInputStreamInMultipartReq) {
            return super.getParameterValues(name);
        }
        return parameterMap.get(name);
    }

    @Override
    public String getParameter(String name) {
        if (this.isMultipartRequest && !this.readInputStreamInMultipartReq) {
            return super.getParameter(name);
        }
        String[] values = getParameterValues(name);
        return values != null && values.length > 0 ? values[0] : null;
    }

    public String getReqParams() {
        return reqParams;
    }

    public byte[] getCachedBody() {
        return Arrays.copyOf(cachedBody, cachedBody.length);
    }

    public static String getRequestBody(ServletRequest request) {
        if (request instanceof HttpRequestWrapper) {
            return ((HttpRequestWrapper) request).getReqParams();
        } else {
            return "";
        }
    }
}