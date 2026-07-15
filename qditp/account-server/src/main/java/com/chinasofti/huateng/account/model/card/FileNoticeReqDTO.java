package com.chinasofti.huateng.account.model.card;

/**
 * 文件通知请求报文。
 */
public class FileNoticeReqDTO {
    /**
     * 文件名。
     */
    private String fileName;

    /**
     * 文件路径。
     */
    private String filePath;

    /**
     * 文件日期。
     */
    private String fileDate;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileDate() {
        return fileDate;
    }

    public void setFileDate(String fileDate) {
        this.fileDate = fileDate;
    }
}
