package com.chinasofti.huateng.acc.es.server.util;

import org.springframework.util.StringUtils;


public class ByteConvertUtil {


    public static byte[] subByteArr(byte[] data, int start, int length) {
        if (length<=0) {
            return new byte[0];
        }
        byte[] value = new byte[length];
        if (data.length - start >= length) {
            System.arraycopy(data, start, value, 0, length);
        }
        return value;
    }




    public static int bytesToInt2(byte[] src) {
        int value;
        value = (int) (((src[0] & 0xFF) << 24) | ((src[01] & 0xFF) << 16)
                | ((src[02] & 0xFF) << 8) | (src[03] & 0xFF));
        return value;
    }


    public static byte[] int2Bytes(int value) {
        byte[] data = new byte[4];
        data[0] = (byte) (value >> 24);
        data[1] = (byte) (value >> 16 & 0xFF);
        data[2] = (byte) (value >> 8 & 0xFF);
        data[3] = (byte) (value & 0xFF);
        return data;
    }



    public static byte[] mergeByteArray(byte[]... args) {
        int length = 0;
        int offset = 0;
        for (byte[] arg : args) {
            length += arg.length;
        }
        byte[] retVal = new byte[length];

        for (byte[] arg : args) {
            System.arraycopy(arg, 0, retVal, offset, arg.length);
            offset += arg.length;
        }
        return retVal;

    }



    public static byte[] AppendByte(byte[] bytes, byte b) {

        return mergeByteArray(bytes, new byte[] { b });
    }

    public static String addZeroForNum(String str,int strLength) {
        if(StringUtils.isEmpty(str)) {
            str = "0";
        }
        int strLen =str.length();
        if (strLen <strLength) {
            while (strLen< strLength) {
                StringBuffer sb = new StringBuffer();
                sb.append("0").append(str);
                str= sb.toString();
                strLen= str.length();
            }
        }

        return str;
    }

    public static String addZeroRight(String str,int strLength) {
        if(StringUtils.isEmpty(str)) {
            str = "0";
        }
        int strLen =str.length();
        if (strLen <strLength) {
            while (strLen< strLength) {
                StringBuffer sb = new StringBuffer();
                sb.append(str).append("0");
                str= sb.toString();
                strLen= str.length();
            }
        }

        return str;
    }

    public static String addSpaceRight(String str,int strLength) {
        if(StringUtils.isEmpty(str)) {
            str = "0";
        }
        int strLen =str.length();
        if (strLen <strLength) {
            while (strLen< strLength) {
                StringBuffer sb = new StringBuffer();
                sb.append(str).append(" ");
                str= sb.toString();
                strLen= str.length();
            }
        }

        return str;
    }


}
