//$Id: Utils.java,v 1.1.1.1 2007/10/06 13:47:03 benmoez Exp $

/**
 * Author : Moez Ben MBarka Moez
 * <p>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


// $Id: Utils.java,v 1.1.1.1 2007/10/06 13:47:03 benmoez Exp $

package com.chinasofti.huateng.acc.security.server.util.sm2;


import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;

/**
 * Some util methods.
 *
 * @author Moez Ben MBarka
 * @version $Revision: 1.1.1.1 $
 */
public class Utils {

    private static final char[] HEX_DIGITS =
            {
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
            };

    private static int fromDigit(char ch) {
        if (ch >= '0' && ch <= '9')
            return ch - '0';
        if (ch >= 'A' && ch <= 'F')
            return ch - 'A' + 10;
        if (ch >= 'a' && ch <= 'f')
            return ch - 'a' + 10;
        throw new IllegalArgumentException("invalid hex digit '" + ch + "'");
    }

    /**
     * Returns a hex string representing the byte array.
     *
     * @param ba The byte array to hexify.
     * @return The hex string.
     */
    public static String toHexString(byte[] ba) {
        int length = ba.length;
        char[] buf = new char[length * 3];
        for (int i = 0, j = 0, k; i < length; ) {
            k = ba[i++];
            buf[j++] = HEX_DIGITS[(k >> 4) & 0x0F];
            buf[j++] = HEX_DIGITS[k & 0x0F];
            buf[j++] = ' ';
        }
        return new String(buf, 0, buf.length - 1);
    }

    public static String toHexStringNoBlank(byte[] ba) {
        int length = ba.length;
        char[] buf = new char[length * 2];
        for (int i = 0, j = 0, k; i < length; ) {
            k = ba[i++];
            buf[j++] = HEX_DIGITS[(k >> 4) & 0x0F];
            buf[j++] = HEX_DIGITS[k & 0x0F];
        }
        return new String(buf, 0, buf.length);
    }

    public static byte[] bytesFromHexString(String hex) throws NumberFormatException {
        if (hex.length() == 0) return null;
        String myhex = hex + " ";
        int len = myhex.length();
        if ((len % 3) != 0) throw new NumberFormatException();
        byte[] buf = new byte[len / 3];
        int i = 0, j = 0;
        while (i < len) {
            try {
                buf[j++] = (byte) ((fromDigit(myhex.charAt(i++)) << 4) |
                        fromDigit(myhex.charAt(i++)));
            } catch (IllegalArgumentException e) {
                throw new NumberFormatException();
            }
            if (myhex.charAt(i++) != ' ') throw new NumberFormatException();
        }
        return buf;
    }

    public static byte[] bytesFromHexStringNoBlank(String hex) throws NumberFormatException {
        if (hex.length() == 0) return null;
        String myhex = hex;
        int len = myhex.length();
        if ((len % 2) != 0) throw new NumberFormatException();
        byte[] buf = new byte[len / 2];
        int i = 0, j = 0;
        while (i < len) {
            try {
                buf[j++] = (byte) ((fromDigit(myhex.charAt(i++)) << 4) |
                        fromDigit(myhex.charAt(i++)));
            } catch (IllegalArgumentException e) {
                throw new NumberFormatException();
            }
        }
        return buf;
    }

    public static byte[] buildHeader(byte cla, byte ins, byte p1, byte p2, byte lc) {
        byte[] header = {cla, ins, p1, p2, lc};
        return header;
    }

    public static byte[] rand_bytes(int size) {
        Random rand = new Random();
        byte[] result = new byte[size];
        rand.nextBytes(result);
        return result;
    }

    public static byte[] clone_array(byte[] src) {
        byte[] dest = new byte[src.length];
        System.arraycopy(src, 0, dest, 0, src.length);
        return dest;
    }

    public static byte[] SHA1(byte[] data) {

        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        md.update(data);

        return md.digest();
    }

    public static byte[] getBERLen(int len) {

        byte[] len_b = null;

        if (len >= 0x100) {
            len_b = new byte[3];
            len_b[0] = (byte) 0x82;
            len_b[1] = (byte) (len >> 8);
            len_b[2] = (byte) (len & 0xFF);
        } else if (len >= 0x80) {
            len_b = new byte[2];
            len_b[0] = (byte) 0x81;
            len_b[1] = (byte) len;
        } else {
            len_b = new byte[]{(byte) len};
        }

        return len_b;
    }

    public static String encryptBASE64(byte[] key) throws Exception {
        return Base64.getEncoder().encodeToString(key);
    }

    public static byte[] decryptBASE64(String key) throws Exception {
        return Base64.getDecoder().decode(key);
    }

    public static byte[] IntToByte(int num) {
        byte bytes[] = new byte[4];
        bytes[0] = (byte) (0xff & num >> 0);
        bytes[1] = (byte) (0xff & num >> 8);
        bytes[2] = (byte) (0xff & num >> 16);
        bytes[3] = (byte) (0xff & num >> 24);
        return bytes;
    }

    public static byte[] asUnsigned32ByteArray(BigInteger n) {
        return asUnsignedNByteArray(n, 32);
    }

    public static byte[] asUnsignedNByteArray(BigInteger x, int length) {
        if (x == null)
            return null;
        byte tmp[] = new byte[length];
        int len = x.toByteArray().length;
        if (len > length + 1)
            return null;
        if (len == length + 1) {
            if (x.toByteArray()[0] != 0) {
                return null;
            } else {
                System.arraycopy(x.toByteArray(), 1, tmp, 0, length);
                return tmp;
            }
        } else {
            System.arraycopy(x.toByteArray(), 0, tmp, length - len, len);
            return tmp;
        }
    }

    public static BigInteger Byte32BigInteger(byte[] inData) {
        String s = new String();
        if (inData.length != 32)
            return null;
        for (int i = 0; i < 32; ++i)
            s = s + String.format("%02X", new Object[]{Byte.valueOf(inData[i])});

        BigInteger bi = new BigInteger(s, 16);
        return bi;
    }

    public static byte[] BigInteger32Byte(BigInteger iB) {
        byte[] ret = new byte[32];
        byte[] tmp = iB.toByteArray();
        if ((tmp[0] == 0) && (tmp[1] < 0) && (tmp.length == 33))
            System.arraycopy(tmp, 1, ret, 0, 32);
        else System.arraycopy(tmp, 0, ret, 0, tmp.length);

        return ret;
    }
}


