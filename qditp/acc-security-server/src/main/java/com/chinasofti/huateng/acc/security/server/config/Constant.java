package com.chinasofti.huateng.acc.security.server.config;


public final class Constant {

    public static final class DataPackage {

        public static final byte START = (byte) 0xEB;

        public static final byte END = 0x03;

        public static final byte DATA_TYPE_QUERY = 0x01;

        public static final byte DATA_TYPE_EXIST = 0x03;

        public static final byte DATA_TYPE_NONE = 0x02;

        public static final int DATA_HEADER_LENGTH = 5;

        public static final byte[] DATA_EXIST_HEADER = {START, DATA_TYPE_EXIST, 0};

        public static final byte[] DATA_QUERY_PACKAGE = {START, DATA_TYPE_QUERY, 0, 0, 0, END};

        public static final byte[] DATA_NONE_PACKAGE = {START, DATA_TYPE_NONE, 0, 0, 0, END};
    }

    public static final class NETTY_LENGTH {

        public static final int MAX_MESSAGE_LENGTH = 1024 * 1024;

        public static final int MIN_MESSAGE_LENGTH = 6;

        public static final int LENGTH_FIELD_OFFSET = 3;

        public static final int LENGTH_FIELD_LENGTH = 2;

        public static final int LENGTH_ADJUSTMENT = 1;

        public static final int INITIAL_BYTES_TO_STRIP = 0;
    }

    public static final class STRATEGY_PATH {

        public static final String COMMON_PATH = "com.chinasofti.huateng.itp.security.server.service.bak.Strategy.";
    }


    public static final class OverTime {

        public static final int POLL_TIME = 1;

        public static final int TIME = 2000;
    }


    public static final class Log {

        public static final String GET_CERTIFICATE = "【发卡机构公钥证书获取】";

        public static final String GET_PUBLICKEY = "【指定索引公钥查询】";

        public static final String GET_SIGN = "【二维码私钥签名验算】";

        public static final String GET_CENTERCODEMAC = "【中心校验码计算】";

        public static final String GET_PASSCODEKEY = "【二维码码体加解密秘钥计算】";

        public static final String GET_TAC = "【公共TAC计算】";
    }


    public static final class RESOPNSE_LENGTH {

        public static final int CENTER_TAC = 37;

        public static final int PASSCODE_KEY = 45;

        public static final int PUBLICKEY_SIGN = 93;
    }


    public static final class ORDER_TYPE {

        public static final String BYTE0 = "00";

        public static final String BYTE8 = "01";

        public static final String BYTE10 = "10";
    }
}
