package com.chinasofti.huateng.acc.es.server.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class AlgorithmUtils {

    /**
     * 数据库行转列List操作
     *
     * @param list
     * @param <T>
     * @return
     * @throws IllegalAccessException
     */
    public static <T> List<List<String>> convert(List<T> list) throws IllegalAccessException {

        Field[] declaredFields = list.get(1).getClass().getDeclaredFields();

        List<List<String>> convertedTable = new ArrayList<List<String>>();

        for (Field field : declaredFields) {
            field.setAccessible(true);
            ArrayList<String> rowLine = new ArrayList<String>();
            for (int i = 0, size = list.size(); i < size; i++) {
                if (i == 0) {
                    rowLine.add(field.getName());
                }
                else {
                    T t = list.get(i);
                    String val = (String) field.get(t);
                    System.out.println(val);
                    rowLine.add(val);
                }
            }
            convertedTable.add(rowLine);
        }
        return convertedTable;
    }

    public static String addZeroForString(String str, int strLength) {
        int strLen = str.length();
        if (strLen < strLength) {
            while (strLen < strLength) {
                StringBuffer sb = new StringBuffer();
                sb.append("0").append(str);
                str = sb.toString();
                strLen = str.length();
            }
        }
        return str;
    }

    /**
     * int转换为小端byte[]（高位放在高地址中）
     *
     * @param iValue
     * @return
     */
    public static byte[] Int2Bytes_LE(int iValue) {
        byte[] rst = new byte[4];
        rst[0] = (byte) (iValue & 0xFF);
        rst[1] = (byte) ((iValue & 0xFF00) >> 8);
        rst[2] = (byte) ((iValue & 0xFF0000) >> 16);
        rst[3] = (byte) ((iValue & 0xFF000000) >> 24);
        return rst;
    }

    /**
     * int转换为大端byte[]（低放在高地址中）
     *
     * @param iValue
     * @return
     */
    public static byte[] Int2Bytes_BE(int iValue) {
        byte[] rst = new byte[4];
        rst[3] = (byte) (iValue & 0xFF);
        rst[2] = (byte) ((iValue & 0xFF00) >> 8);
        rst[1] = (byte) ((iValue & 0xFF0000) >> 16);
        rst[0] = (byte) ((iValue & 0xFF000000) >> 24);
        return rst;
    }

    /**
     * 转换byte数组为int（小端）
     *
     * @return
     * @note 数组长度至少为4，按小端方式转换,即传入的bytes是小端的，按这个规律组织成int
     */
    public static int Bytes2Int_LE(byte[] bytes) {
        if (bytes.length < 4)
            return -1;
        int iRst = (bytes[0] & 0xFF);
        iRst |= (bytes[1] & 0xFF) << 8;
        iRst |= (bytes[2] & 0xFF) << 16;
        iRst |= (bytes[3] & 0xFF) << 24;

        return iRst;
    }

    /**
     * 转换byte数组为int（大端）
     *
     * @return
     * @note 数组长度至少为4，按小端方式转换，即传入的bytes是大端的，按这个规律组织成int
     */
    public static int Bytes2Int_BE(byte[] bytes) {
        if (bytes.length < 4)
            return -1;
        int iRst = (bytes[0] << 24) & 0xFF;
        iRst |= (bytes[1] << 16) & 0xFF;
        iRst |= (bytes[2] << 8) & 0xFF;
        iRst |= bytes[3] & 0xFF;

        return iRst;
    }

    /**
     * 转换byte数组为Char（小端）
     *
     * @return
     * @note 数组长度至少为2，按小端方式转换
     */
    public static char Bytes2Char_LE(byte[] bytes) {
        if (bytes.length < 2)
            return (char) -1;
        int iRst = (bytes[0] & 0xFF);
        iRst |= (bytes[1] & 0xFF) << 8;

        return (char) iRst;
    }

    /**
     * 转换byte数组为char（大端）
     *
     * @return
     * @note 数组长度至少为2，按小端方式转换
     */
    public static char Bytes2Char_BE(byte[] bytes) {
        if (bytes.length < 2)
            return (char) -1;
        int iRst = (bytes[0] << 8) & 0xFF;
        iRst |= bytes[1] & 0xFF;

        return (char) iRst;
    }

    /**
     * 转换String为byte[]
     *
     * @param str
     * @return
     */
    public static byte[] String2Bytes_LE(String str) {
        if (str == null) {
            return null;
        }
        char[] chars = str.toCharArray();

        byte[] rst = Chars2Bytes_LE(chars);

        return rst;
    }

    /**
     * 转换字符数组为定长byte[]
     *
     * @param chars 字符数组
     * @return 若指定的定长不足返回null, 否则返回byte数组
     */
    public static byte[] Chars2Bytes_LE(char[] chars) {
        if (chars == null)
            return null;

        int iCharCount = chars.length;
        byte[] rst = new byte[iCharCount * 2];
        int i = 0;
        for (i = 0; i < iCharCount; i++) {
            rst[i * 2] = (byte) (chars[i] & 0xFF);
            rst[i * 2 + 1] = (byte) ((chars[i] & 0xFF00) >> 8);
        }

        return rst;
    }

    /**
     * byte转HexString
     *
     * @param
     * @param
     * @param
     * @return
     */
    public static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    /**
     * short转换为小端byte[]（高位放在高地址中）
     *
     * @param iValue
     * @return
     */
    public static byte[] Short2Bytes_LE(short iValue) {
        byte[] rst = new byte[2];
        rst[0] = (byte) (iValue & 0xFF);
        rst[1] = (byte) ((iValue & 0xFF00) >> 8);
        return rst;
    }

    /**
     * short转换为大端byte[]（低位放在高地址中）
     *
     * @param iValue
     * @return
     */
    public static byte[] Short2Bytes_BE(short iValue) {
        byte[] rst = new byte[2];
        rst[1] = (byte) (iValue & 0xFF);
        rst[0] = (byte) ((iValue & 0xFF00) >> 8);
        return rst;
    }

    /**
     * 从一个byte[]数组中截取一部分
     *
     * @param src
     * @param begin
     * @param count
     * @return
     */
    public static byte[] subBytes(byte[] src, int begin, int count) {
        byte[] bs = new byte[count];
        for (int i = begin; i < begin + count; i++) bs[i - begin] = src[i];
        return bs;
    }
}
