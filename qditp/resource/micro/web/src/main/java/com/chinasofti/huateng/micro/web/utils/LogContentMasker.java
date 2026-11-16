package com.chinasofti.huateng.micro.web.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志内容屏蔽器。
 *
 * <p>员工码照片（photoUrl / photo）是 base64 大字段（实测上万字符），
 * 被 BodyCacheFilter / InternalMicroHttp 整段打进日志会冲刷关键信息。
 * 这里统一把这类字段的值替换为长度占位。</p>
 */
public final class LogContentMasker {

    /**
     * 命中 "photoUrl":"..." 与 \"photoUrl\":\"...\"（bizData 内嵌 JSON 的转义形态）两种写法。
     * base64 字母表不含引号与反斜杠，值域用 [^"]* 安全截断。
     */
    private static final Pattern BASE64_FIELD = Pattern.compile(
            "(\\\\?\"(?:photoUrl|photo)\\\\?\"\\s*:\\s*\\\\?\")([^\"]*)(\\\\?\")");

    private LogContentMasker() {
    }

    /**
     * 把 base64 大字段的值替换为 {@code <base64,len=N>} 占位。
     *
     * @param content 原始日志内容
     * @return 屏蔽后的内容
     */
    public static String maskBase64Fields(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        Matcher m = BASE64_FIELD.matcher(content);
        if (!m.find()) {
            return content;
        }
        StringBuffer sb = new StringBuffer(content.length());
        do {
            String replacement = m.group(1) + "<base64,len=" + m.group(2).length() + ">" + m.group(3);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        } while (m.find());
        m.appendTail(sb);
        return sb.toString();
    }
}
