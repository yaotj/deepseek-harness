package com.chinasofti.huateng.acc.es.server.netty.model;


public final class Constant {

    public static final class FileMessage {

        public static final String FILE_FOLDER = "/UPLOAD";

        public static final String  FILE_NAME_TASK_PREFIX = "9050";

        public static final String FILE_NAME_CUSTOM_PREFIX = "9060";

        public static final String FILE_NAME_CUSTOM_REPORT_PREFIX = "9061";
    }



    public static final class DataPackage {

        public static final String TXN_TYPE_7000 = "7000";

        public static final String TXN_TYPE_7002 = "7002";

        public static final String TXN_TYPE_7003 = "7003";

        public static final String TXN_TYPE_7004 = "7004";

        public static final String TXN_TYPE_7005 = "7005";

        public static final byte RESULT_TYPE = (byte)'1';
    }


    public static final class MackStatus {

        public static final String NORMAL = "00";

        public static final String MD5_ERROR = "01";

        public static final String MESSAGE_ERROR = "02";

        public static final String WORK_ERROR = "03";


        public static final String ES_NODE_ERROR = "04";


        public static final String OPERATOR_ERROR = "05";


        public static final String PASSWORD_ERROR = "06";

        public static final String INVALID_FILE_NAME = "10";

        public static final String FILE_NOT_EXIT = "11";

        public static final String FILE_GET_ERROR = "12";

        public static final String OTHER = "FF";
    }


    public static final class DataLength {

        public static final int DATA_LENGTH_BYTES = 4;

        public static final int DATA_HEAD_BYTES = 26;

        public static final int DATA_7000_SIGN_BYTES = 76;

        public static final int DATA_BODY_7000_SIGN_BYTES = 50;

        public static final int DATA_BODY_7000_SIGN_BYTES_MACK = 8;

        public static final int DATA_BODY_7002_TASK_BYTES = 104;

    }

    public static final class NETTY_LENGTH {

        public static final int MAX_MESSAGE_LENGTH = 1024 * 1024;

        public static final int MIN_MESSAGE_LENGTH = 4;

        public static final int LENGTH_FIELD_OFFSET = 0;

        public static final int LENGTH_FIELD_LENGTH = 4;

        public static final int LENGTH_ADJUSTMENT = 0;

        public static final int INITIAL_BYTES_TO_STRIP = 0;
    }
}
