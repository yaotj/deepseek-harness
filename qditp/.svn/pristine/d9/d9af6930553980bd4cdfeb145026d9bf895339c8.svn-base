package com.chinasofti.huateng.collectpay.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by tian on 2022/6/24.
 */
@Component
@Slf4j
public class DateUtils {

    public String getTime(int c, String format) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, c);
        return new SimpleDateFormat(format).format(cal.getTime());
    }

    /**
     * @return yyyy-MM-dd HH:mm:ss
     */
    public static String getNowTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }


    /**
     * 获取指定日期的前后几天日期
     *
     * @param date yyyy/MM/dd
     */
    public String getBeforeOrAfrerTime(String date, int c, String format) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(date));
        cal.add(Calendar.DATE, c);
        return new SimpleDateFormat(format).format(cal.getTime());
    }

    /**
     * 获取指定日期的前后几分钟时间
     * @param minuteTime 精确到分钟的时间 yyyy/MM/dd HH:mm   eg:2022/08/08 11:11
     */
    public static String getBeforeOrAfrerTimeOfMinute(String minuteTime, int c, String format) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(minuteTime));
        cal.add(Calendar.MINUTE, c);
        return new SimpleDateFormat(format).format(cal.getTime());
    }

}