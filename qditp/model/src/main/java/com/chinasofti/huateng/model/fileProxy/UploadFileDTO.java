package com.chinasofti.huateng.model.fileProxy;

public class UploadFileDTO {

    private String fileName;


    private String fileType;


    private String operator;


    private String optType;


    private String srcPath;


    private String targetPath;

    private boolean saveToDb = true;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getOptType() {
        return optType;
    }

    public void setOptType(String optType) {
        this.optType = optType;
    }

    public String getSrcPath() {
        return srcPath;
    }

    public void setSrcPath(String srcPath) {
        this.srcPath = srcPath;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public boolean isSaveToDb() {
        return saveToDb;
    }

    public void setSaveToDb(boolean saveToDb) {
        this.saveToDb = saveToDb;
    }
}
