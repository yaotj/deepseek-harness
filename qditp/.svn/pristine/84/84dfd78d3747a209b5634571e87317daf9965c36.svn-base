package com.chinasofti.huateng.acc.security.server.config;

/**
 * Description:
 *
 * @author houkepan
 * @date 2019/2/18 9:53
 */
public final class Constant {

    public static final class DataPackage {
        /**
         * 包体开始标识
         */
        public static final byte START = (byte) 0xEB;
        /**
         * 包体结束标识
         */
        public static final byte END = 0x03;
        /**
         * 查询包数据类型
         */
        public static final byte DATA_TYPE_QUERY = 0x01;
        /**
         * 含有数据的消息包数据类型
         */
        public static final byte DATA_TYPE_EXIST = 0x03;
        /**
         * 没有数据消息报数据类型
         */
        public static final byte DATA_TYPE_NONE = 0x02;
        /**
         * 消息头长度
         */
        public static final int DATA_HEADER_LENGTH = 5;
        /**
         * 含有数据包包头 开始标识+数据类型(0x03)+序列号
         */
        public static final byte[] DATA_EXIST_HEADER = {START, DATA_TYPE_EXIST, 0};
        /**
         * 查询包结构 开始标识+数据类型(0x01) +序列号+数据长度(两位)+结尾
         */
        public static final byte[] DATA_QUERY_PACKAGE = {START, DATA_TYPE_QUERY, 0, 0, 0, END};
        /**
         * 没有数据消息包 开始标识+数据类型(0x02)+序列号
         */
        public static final byte[] DATA_NONE_PACKAGE = {START, DATA_TYPE_NONE, 0, 0, 0, END};
    }

    public static final class NETTY_LENGTH {
        /**
         * 最大长度
         */
        public static final int MAX_MESSAGE_LENGTH = 1024 * 1024;
        /**
         * 最小长度
         */
        public static final int MIN_MESSAGE_LENGTH = 6;
        /**
         * 长度偏移
         */
        public static final int LENGTH_FIELD_OFFSET = 3;
        /**
         * 长度字段所占的字节数
         */
        public static final int LENGTH_FIELD_LENGTH = 2;
        /**
         * 消息尾，结束标识长度
         */
        public static final int LENGTH_ADJUSTMENT = 1;
        /**
         * 忽略字节
         */
        public static final int INITIAL_BYTES_TO_STRIP = 0;
    }

    public static final class STRATEGY_PATH {
        /**
         * 策略模式公共长度
         */
        public static final String COMMON_PATH = "com.chinasofti.huateng.itp.security.server.service.bak.Strategy.";
    }

    /**
     * 超时时间
     */
    public static final class OverTime {
        /**
         * 轮询时间 毫秒
         */
        public static final int POLL_TIME = 1;
        /**
         * 超时时间 毫秒
         */
        public static final int TIME = 2000;
    }

    /**
     * 日志
     */
    public static final class Log {
        /**
         * 发卡机构公钥证书获取
         */
        public static final String GET_CERTIFICATE = "【发卡机构公钥证书获取】";
        /**
         * 指定索引公钥查询
         */
        public static final String GET_PUBLICKEY = "【指定索引公钥查询】";
        /**
         * 二维码私钥签名计算
         */
        public static final String GET_SIGN = "【二维码私钥签名验算】";
        /**
         * 中心校验码计算
         */
        public static final String GET_CENTERCODEMAC = "【中心校验码计算】";
        /**
         * 二维码码体加解密秘钥计算
         */
        public static final String GET_PASSCODEKEY = "【二维码码体加解密秘钥计算】";
        /**
         * 公共TAC计算
         */
        public static final String GET_TAC = "【公共TAC计算】";
    }

    /**
     * 应答消息值长度(最后字段)
     */
    public static final class RESOPNSE_LENGTH {
        /**
         * 中心校验码应答值、TAC应答值
         */
        public static final int CENTER_TAC = 37;
        /**
         * passcodekey应答值
         */
        public static final int PASSCODE_KEY = 45;
        /**
         * 公钥值、签名值
         */
        public static final int PUBLICKEY_SIGN = 93;
    }

    /**
     * 加密机不同操作
     */
    public static final class ORDER_TYPE {
        /**
         * 返回 0 字节
         */
        public static final String BYTE0 = "00";
        /**
         * 返回 8 字节
         */
        public static final String BYTE8 = "01";
        /**
         * 返回 10 字节
         */
        public static final String BYTE10 = "10";
    }
}
