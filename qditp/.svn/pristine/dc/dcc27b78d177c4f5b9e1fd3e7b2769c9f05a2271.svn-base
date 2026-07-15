package com.chinasofti.huateng.acc.es.server.netty.model;

/**
 *
 */
public final class Constant {

    public static final class FileMessage {

        public static final String FILE_FOLDER = "/UPLOAD";

        public static final String  FILE_NAME_TASK_PREFIX = "9050";

        public static final String FILE_NAME_CUSTOM_PREFIX = "9060";

        public static final String FILE_NAME_CUSTOM_REPORT_PREFIX = "9061";
    }



    public static final class DataPackage {
        /**
         * 7000交易报文
         */
        public static final String TXN_TYPE_7000 = "7000";
        /**
         * 7002交易报文
         */
        public static final String TXN_TYPE_7002 = "7002";
        /**
         * 7003交易报文
         */
        public static final String TXN_TYPE_7003 = "7003";
        /**
         * 7004交易报文
         */
        public static final String TXN_TYPE_7004 = "7004";
        /**
         * 7005交易报文
         */
        public static final String TXN_TYPE_7005 = "7005";
        /**
         * 包头请求应答标识-应答
         */
        public static final byte RESULT_TYPE = (byte)'1';
    }

    /**
     * mack应答码定义
     */
    public static final class MackStatus {
        /**
         * 正常-00
         */
        public static final String NORMAL = "00";
        /**
         * 报文格式错误
         */
        public static final String MD5_ERROR = "01";
        /**
         * 无效的消息分类/类型码
         */
        public static final String MESSAGE_ERROR = "02";
        /**
         * 无效的数值范围
         */
        public static final String WORK_ERROR = "03";

        /**
         * 无效的节点标识码
         */
        public static final String ES_NODE_ERROR = "04";

        /**
         * 无效的操作员
         */
        public static final String OPERATOR_ERROR = "05";

        /**
         * 操作员密码错误
         */
        public static final String PASSWORD_ERROR = "06";

        public static final String INVALID_FILE_NAME = "10";

        public static final String FILE_NOT_EXIT = "11";

        public static final String FILE_GET_ERROR = "12";

        public static final String OTHER = "FF";
    }


    public static final class DataLength {
        /**
         * 包长度字节
         */
        public static final int DATA_LENGTH_BYTES = 4;
        /**
         * 包头长度字节
         */
        public static final int DATA_HEAD_BYTES = 26;
        /**
         * 7000设备签到报文长度（不包含长度字段，包头 + 包体 + mac检验码）
         */
        public static final int DATA_7000_SIGN_BYTES = 76;//不包括mac108
        /**
         * 7000设备签到报文体长度
         */
        public static final int DATA_BODY_7000_SIGN_BYTES = 50;
        /**
         * 7000设备签到报文MAC报文体长度
         */
        public static final int DATA_BODY_7000_SIGN_BYTES_MACK = 8;

        public static final int DATA_BODY_7002_TASK_BYTES = 104;

    }

    public static final class NETTY_LENGTH {
        /**
         * 最大长度
         */
        public static final int MAX_MESSAGE_LENGTH = 1024 * 1024;
        /**
         * 最小长度
         */
        public static final int MIN_MESSAGE_LENGTH = 4;
        /**
         * 长度偏移
         */
        public static final int LENGTH_FIELD_OFFSET = 0;
        /**
         * 长度字段所占的字节数
         */
        public static final int LENGTH_FIELD_LENGTH = 4;
        /**
         * 消息尾，结束标识长度
         */
        public static final int LENGTH_ADJUSTMENT = 0;
        /**
         * 忽略字节
         */
        public static final int INITIAL_BYTES_TO_STRIP = 0;
    }
}
