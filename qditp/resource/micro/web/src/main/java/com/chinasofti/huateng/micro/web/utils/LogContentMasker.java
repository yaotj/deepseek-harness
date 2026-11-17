package com.chinasofti.huateng.micro.web.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class LogContentMasker {


    private static final Pattern BASE64_FIELD = Pattern.compile(
            "(\\\\?\"(?:photoUrl|photo)\\\\?\"\\s*:\\s*\\\\?\")([^\"]*)(\\\\?\")");

    private LogContentMasker() {
    }


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
