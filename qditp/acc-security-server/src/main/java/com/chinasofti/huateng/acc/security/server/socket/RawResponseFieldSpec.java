package com.chinasofti.huateng.acc.security.server.socket;

public record RawResponseFieldSpec(int lengthFromIndex, int fixedLength) {

    public static RawResponseFieldSpec fixed(int length) {
        return new RawResponseFieldSpec(-1, length);
    }

    public static RawResponseFieldSpec dynamic(int lengthFromIndex) {
        return new RawResponseFieldSpec(lengthFromIndex, 0);
    }
}
