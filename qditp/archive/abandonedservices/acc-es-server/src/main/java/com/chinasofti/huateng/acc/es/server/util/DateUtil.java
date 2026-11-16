package com.chinasofti.huateng.acc.es.server.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author rxwnc
 */
public class DateUtil {

    public static String date2String8(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        return format.format(date);
    }

    public static String dateString8() {
        return date2String8(new Date());
    }
}
