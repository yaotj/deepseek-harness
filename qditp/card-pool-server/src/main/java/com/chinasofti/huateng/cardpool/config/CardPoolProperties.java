package com.chinasofti.huateng.cardpool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 逻辑卡号池配置，前缀 {@code card-pool}。 */
@Component
@ConfigurationProperties(prefix = "card-pool")
public class CardPoolProperties {

    /** 可用卡号低水位，低于该值触发补货。 */
    private int threshold = 10000;

    /** 单批次向 ACC 申请的卡号数量。 */
    private int requestNum = 100000;

    /** 批次号序列接近上限的告警阈值。 */
    private long sequenceAlertThreshold = 999900L;

    /** 预占有效分钟数，超时由维护动作回收。 */
    private int reservationMinutes = 10;

    /** 票种级批次锁的失效秒数，需大于单批次最长导入耗时；导入过程中会持续续期。 */
    private long lockStaleSeconds = 1800L;

    /** 单次导入的分片行数。 */
    private int importChunkSize = 1000;

    /** FTP 配置。 */
    private final Ftp ftp = new Ftp();

    /**
     * 读取可用卡号低水位。
     *
     * @return 低水位阈值，可用卡号低于该值触发补货
     */
    public int getThreshold() {
        return threshold;
    }

    /**
     * 设置可用卡号低水位。
     *
     * @param threshold 低水位阈值
     */
    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    /**
     * 读取单批次向 ACC 申请的卡号数量。
     *
     * @return 单批次申请数量
     */
    public int getRequestNum() {
        return requestNum;
    }

    /**
     * 设置单批次向 ACC 申请的卡号数量。
     *
     * @param requestNum 单批次申请数量
     */
    public void setRequestNum(int requestNum) {
        this.requestNum = requestNum;
    }

    /**
     * 读取批次号序列接近上限的告警阈值。
     *
     * @return 批次号序列告警阈值
     */
    public long getSequenceAlertThreshold() {
        return sequenceAlertThreshold;
    }

    /**
     * 设置批次号序列接近上限的告警阈值。
     *
     * @param sequenceAlertThreshold 批次号序列告警阈值
     */
    public void setSequenceAlertThreshold(long sequenceAlertThreshold) {
        this.sequenceAlertThreshold = sequenceAlertThreshold;
    }

    /**
     * 读取卡号预占有效时长。
     *
     * @return 预占有效分钟数，超时由维护动作回收
     */
    public int getReservationMinutes() {
        return reservationMinutes;
    }

    /**
     * 设置卡号预占有效时长。
     *
     * @param reservationMinutes 预占有效分钟数
     */
    public void setReservationMinutes(int reservationMinutes) {
        this.reservationMinutes = reservationMinutes;
    }

    /**
     * 读取票种级批次锁的失效时长。
     *
     * @return 批次锁失效秒数，需大于单批次最长导入耗时
     */
    public long getLockStaleSeconds() {
        return lockStaleSeconds;
    }

    /**
     * 设置票种级批次锁的失效时长。
     *
     * @param lockStaleSeconds 批次锁失效秒数
     */
    public void setLockStaleSeconds(long lockStaleSeconds) {
        this.lockStaleSeconds = lockStaleSeconds;
    }

    /**
     * 读取单次导入的分片行数。
     *
     * @return 导入分片行数
     */
    public int getImportChunkSize() {
        return importChunkSize;
    }

    /**
     * 设置单次导入的分片行数。
     *
     * @param importChunkSize 导入分片行数
     */
    public void setImportChunkSize(int importChunkSize) {
        this.importChunkSize = importChunkSize;
    }

    /**
     * 读取 ACC 逻辑卡号文件的 FTP 配置。
     *
     * @return FTP 配置
     */
    public Ftp getFtp() {
        return ftp;
    }

    /** ACC 逻辑卡号文件 FTP 配置。 */
    public static class Ftp {

        /** FTP 主机，未配置时禁止下载。 */
        private String host;

        /** FTP 端口。 */
        private int port = 21;

        /** FTP 用户名。 */
        private String username;

        /** FTP 口令，由 K8s Secret 注入。 */
        private String password;

        /** 逻辑卡号文件所在目录。 */
        private String baseDir = "/";

        /** 允许下载的最大文件字节数。 */
        private long maxFileSize = 52428800L;

        /** 控制连接建立超时毫秒数。 */
        private int connectTimeoutMillis = 10000;

        /** 控制连接读写超时毫秒数。 */
        private int soTimeoutMillis = 30000;

        /** 数据连接超时秒数，覆盖 listFiles 与 retrieveFile。 */
        private int dataTimeoutSeconds = 120;

        /**
         * 读取 FTP 主机。
         *
         * @return FTP 主机，未配置时禁止下载
         */
        public String getHost() {
            return host;
        }

        /**
         * 设置 FTP 主机。
         *
         * @param host FTP 主机
         */
        public void setHost(String host) {
            this.host = host;
        }

        /**
         * 读取 FTP 端口。
         *
         * @return FTP 端口
         */
        public int getPort() {
            return port;
        }

        /**
         * 设置 FTP 端口。
         *
         * @param port FTP 端口
         */
        public void setPort(int port) {
            this.port = port;
        }

        /**
         * 读取 FTP 用户名。
         *
         * @return FTP 用户名
         */
        public String getUsername() {
            return username;
        }

        /**
         * 设置 FTP 用户名。
         *
         * @param username FTP 用户名
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * 读取 FTP 口令，由 K8s Secret 注入。
         *
         * @return FTP 口令
         */
        public String getPassword() {
            return password;
        }

        /**
         * 设置 FTP 口令。
         *
         * @param password FTP 口令
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * 读取逻辑卡号文件所在目录。
         *
         * @return 逻辑卡号文件目录
         */
        public String getBaseDir() {
            return baseDir;
        }

        /**
         * 设置逻辑卡号文件所在目录。
         *
         * @param baseDir 逻辑卡号文件目录
         */
        public void setBaseDir(String baseDir) {
            this.baseDir = baseDir;
        }

        /**
         * 读取允许下载的最大文件大小。
         *
         * @return 最大文件字节数
         */
        public long getMaxFileSize() {
            return maxFileSize;
        }

        /**
         * 设置允许下载的最大文件大小。
         *
         * @param maxFileSize 最大文件字节数
         */
        public void setMaxFileSize(long maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        /**
         * 读取控制连接建立超时时间。
         *
         * @return 控制连接建立超时毫秒数
         */
        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        /**
         * 设置控制连接建立超时时间。
         *
         * @param connectTimeoutMillis 控制连接建立超时毫秒数
         */
        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        /**
         * 读取控制连接读写超时时间。
         *
         * @return 控制连接读写超时毫秒数
         */
        public int getSoTimeoutMillis() {
            return soTimeoutMillis;
        }

        /**
         * 设置控制连接读写超时时间。
         *
         * @param soTimeoutMillis 控制连接读写超时毫秒数
         */
        public void setSoTimeoutMillis(int soTimeoutMillis) {
            this.soTimeoutMillis = soTimeoutMillis;
        }

        /**
         * 读取数据连接超时时间，覆盖 listFiles 与 retrieveFile。
         *
         * @return 数据连接超时秒数
         */
        public int getDataTimeoutSeconds() {
            return dataTimeoutSeconds;
        }

        /**
         * 设置数据连接超时时间，覆盖 listFiles 与 retrieveFile。
         *
         * @param dataTimeoutSeconds 数据连接超时秒数
         */
        public void setDataTimeoutSeconds(int dataTimeoutSeconds) {
            this.dataTimeoutSeconds = dataTimeoutSeconds;
        }


    }
}

