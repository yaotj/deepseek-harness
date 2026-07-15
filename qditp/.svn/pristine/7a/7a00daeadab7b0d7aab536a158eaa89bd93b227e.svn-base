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

        //多少个属性表示多少行，遍历行
        for (Field field : declaredFields) {
            field.setAccessible(true);
            ArrayList<String> rowLine = new ArrayList<String>();
            //list<T>多少个T实体类表示有多少列，遍历列
            for (int i = 0, size = list.size(); i < size; i++) {
                //每一行的第一列对应T字段名
                //所以新table的第一列要设置为字段名
                if (i == 0) {
                    rowLine.add(field.getName());
                }
                //新table从第二列开始，某一列的某个值对应旧table第一列的某个字段
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
                sb.append("0").append(str);//左补0
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
        // 先写int的最后一个字节
        rst[0] = (byte) (iValue & 0xFF);
        // int 倒数第二个字节
        rst[1] = (byte) ((iValue & 0xFF00) >> 8);
        // int 倒数第三个字节
        rst[2] = (byte) ((iValue & 0xFF0000) >> 16);
        // int 第一个字节
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
        // 先写int的最后一个字节
        rst[3] = (byte) (iValue & 0xFF);
        // int 倒数第二个字节
        rst[2] = (byte) ((iValue & 0xFF00) >> 8);
        // int 倒数第三个字节
        rst[1] = (byte) ((iValue & 0xFF0000) >> 16);
        // int 第一个字节
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
        // 先写short的最后一个字节
        rst[0] = (byte) (iValue & 0xFF);
        // short 倒数第二个字节
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
        // 先写short的最后一个字节
        rst[1] = (byte) (iValue & 0xFF);
        // short 倒数第二个字节
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
