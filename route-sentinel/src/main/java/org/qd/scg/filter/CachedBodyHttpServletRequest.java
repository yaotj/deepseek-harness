package org.qd.scg.filter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * 缓存请求体的 HttpServletRequest 包装器。
 *
 * <p>针对 {@code application/x-www-form-urlencoded} 请求：
 * <ul>
 *   <li>在构造时读取原始 body 并缓存，供后续过滤器 / handler 复用；</li>
 *   <li>手动解析 query string + form body，重建 {@code getParameter} 族方法；</li>
 *   <li>避免调用原 request 的 {@code getParameter} 导致 input stream 被容器提前消费。</li>
 * </ul>
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

    private final byte[] cachedBody;
    private final Charset encoding;
    private final Map<String, String[]> parameterMap;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.encoding = resolveEncoding(request);
        // 1. 先保存 query string 中的参数（此时原 request body 还未被读取）
        this.parameterMap = new HashMap<>();
        parseQueryString(request.getQueryString());
        // 2. 读取并缓存 body
        this.cachedBody = readBody(request);
        // 3. 如果是 form-urlencoded，再解析 body 中的参数
        if (isFormUrlEncoded(request) && cachedBody.length > 0) {
            parseFormBody(new String(cachedBody, encoding));
        }
    }

    private Charset resolveEncoding(HttpServletRequest request) {
        String enc = request.getCharacterEncoding();
        if (enc != null && !enc.isBlank()) {
            try {
                return Charset.forName(enc);
            } catch (Exception ignored) {
            }
        }
        return StandardCharsets.UTF_8;
    }

    private boolean isFormUrlEncoded(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        return contentType.toLowerCase().startsWith(FORM_CONTENT_TYPE);
    }

    private byte[] readBody(HttpServletRequest request) throws IOException {
        try (InputStream is = request.getInputStream()) {
            return is.readAllBytes();
        }
    }

    private void parseQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return;
        }
        parseKeyValuePairs(queryString, false);
    }

    private void parseFormBody(String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        parseKeyValuePairs(body, true);
    }

    private void parseKeyValuePairs(String raw, boolean fromBody) {
        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            String key;
            String value;
            if (eq < 0) {
                key = decode(pair);
                value = "";
            } else {
                key = decode(pair.substring(0, eq));
                value = decode(pair.substring(eq + 1));
            }
            if (key.isEmpty()) {
                continue;
            }
            // body 里的参数优先级高于 query string（后 put 覆盖）
            parameterMap.merge(key, new String[]{value}, (old, add) -> {
                String[] merged = new String[old.length + add.length];
                System.arraycopy(old, 0, merged, 0, old.length);
                System.arraycopy(add, 0, merged, old.length, add.length);
                return merged;
            });
        }
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%20"), encoding);
        } catch (Exception e) {
            return value;
        }
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
    }

    @Override
    public String getParameter(String name) {
        String[] values = parameterMap.get(name);
        return (values != null && values.length > 0) ? values[0] : null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.unmodifiableMap(parameterMap);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameterMap.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return parameterMap.get(name);
    }

    /**
     * 返回缓存的原始 body 字节，便于调试或扩展。
     */
    public byte[] getCachedBody() {
        return cachedBody;
    }

    private static class CachedServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream source;

        CachedServletInputStream(byte[] body) {
            this.source = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return source.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() throws IOException {
            return source.read();
        }
    }
}
