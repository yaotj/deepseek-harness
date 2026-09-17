package com.chinasofti.huateng.model.recon;

/**
 * 对账分片行的拼装与拆解工具，四类文件的唯一格式出口。
 */
public final class ReconRecord {

    /** 字段分隔符。 */
    public static final char DELIMITER = '|';

    /** 行分隔符。 */
    public static final char LINE_SEPARATOR = '\n';

    private ReconRecord() {
    }

    /**
     * 把字段数组拼成一行，不含行尾换行符。
     * @param fields 字段值，null 写成空串。
     * @return 管道分隔的一行。
     */
    public static String line(Object... fields) {
        StringBuilder builder = new StringBuilder(128);
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                builder.append(DELIMITER);
            }
            builder.append(sanitize(fields[i]));
        }
        return builder.toString();
    }

    /**
     * 拆解一行，保留末尾空字段。
     * @param line 管道分隔的一行。
     * @return 字段数组。
     */
    public static String[] split(String line) {
        return line.split("\\|", -1);
    }

    /**
     * 净化单个字段：null 转空串，去掉分隔符、回车与换行。
     * @param value 原始值。
     * @return 可安全写入分片的字段文本。
     */
    public static String sanitize(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.indexOf(DELIMITER) < 0 && text.indexOf('\r') < 0 && text.indexOf('\n') < 0) {
            return text;
        }
        return text.replace(DELIMITER, ' ').replace('\r', ' ').replace('\n', ' ');
    }

    /**
     * 解析汇总行的度量字段，缺失或非数字都按 0 处理。
     * @param fields 已拆解的字段数组。
     * @param index 度量字段下标。
     * @return 度量值。
     */
    public static long metric(String[] fields, int index) {
        if (index >= fields.length) {
            return 0L;
        }
        String text = fields[index].trim();
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
