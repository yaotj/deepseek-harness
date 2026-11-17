package com.chinasofti.huateng.model.recon;


public final class ReconRecord {

    public static final char DELIMITER = '|';

    public static final char LINE_SEPARATOR = '\n';

    private ReconRecord() {
    }


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


    public static String[] split(String line) {
        return line.split("\\|", -1);
    }


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
