package com.chinasofti.huateng.cardpool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "card-pool")
public class CardPoolProperties {

    private int threshold = 10000;

    private int requestNum = 100000;

    private long sequenceAlertThreshold = 999900L;

    private int reservationMinutes = 10;

    private long lockStaleSeconds = 1800L;

    private int importChunkSize = 1000;

    private final Ftp ftp = new Ftp();


    public int getThreshold() {
        return threshold;
    }


    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }


    public int getRequestNum() {
        return requestNum;
    }


    public void setRequestNum(int requestNum) {
        this.requestNum = requestNum;
    }


    public long getSequenceAlertThreshold() {
        return sequenceAlertThreshold;
    }


    public void setSequenceAlertThreshold(long sequenceAlertThreshold) {
        this.sequenceAlertThreshold = sequenceAlertThreshold;
    }


    public int getReservationMinutes() {
        return reservationMinutes;
    }


    public void setReservationMinutes(int reservationMinutes) {
        this.reservationMinutes = reservationMinutes;
    }


    public long getLockStaleSeconds() {
        return lockStaleSeconds;
    }


    public void setLockStaleSeconds(long lockStaleSeconds) {
        this.lockStaleSeconds = lockStaleSeconds;
    }


    public int getImportChunkSize() {
        return importChunkSize;
    }


    public void setImportChunkSize(int importChunkSize) {
        this.importChunkSize = importChunkSize;
    }


    public Ftp getFtp() {
        return ftp;
    }

    public static class Ftp {

        private String host;

        private int port = 21;

        private String username;

        private String password;

        private String baseDir = "/";

        private long maxFileSize = 52428800L;

        private int connectTimeoutMillis = 10000;

        private int soTimeoutMillis = 30000;

        private int dataTimeoutSeconds = 120;


        public String getHost() {
            return host;
        }


        public void setHost(String host) {
            this.host = host;
        }


        public int getPort() {
            return port;
        }


        public void setPort(int port) {
            this.port = port;
        }


        public String getUsername() {
            return username;
        }


        public void setUsername(String username) {
            this.username = username;
        }


        public String getPassword() {
            return password;
        }


        public void setPassword(String password) {
            this.password = password;
        }


        public String getBaseDir() {
            return baseDir;
        }


        public void setBaseDir(String baseDir) {
            this.baseDir = baseDir;
        }


        public long getMaxFileSize() {
            return maxFileSize;
        }


        public void setMaxFileSize(long maxFileSize) {
            this.maxFileSize = maxFileSize;
        }


        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }


        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }


        public int getSoTimeoutMillis() {
            return soTimeoutMillis;
        }


        public void setSoTimeoutMillis(int soTimeoutMillis) {
            this.soTimeoutMillis = soTimeoutMillis;
        }


        public int getDataTimeoutSeconds() {
            return dataTimeoutSeconds;
        }


        public void setDataTimeoutSeconds(int dataTimeoutSeconds) {
            this.dataTimeoutSeconds = dataTimeoutSeconds;
        }


    }
}

