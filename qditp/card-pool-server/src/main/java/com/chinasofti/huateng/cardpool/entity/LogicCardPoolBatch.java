package com.chinasofti.huateng.cardpool.entity;

import java.time.LocalDateTime;

public class LogicCardPoolBatch {

    private Long batchNo;

    private String requestSeq;

    private String cardType;

    private String accTicketType;

    private Integer requestNum;

    private String source;

    private String fileName;

    private String ftpPath;

    private Long fileSize;

    private String fileSha256;

    private Integer totalCount;

    private Integer validCount;

    private Integer duplicateCount;

    private Integer invalidCount;

    private String status;

    private String errorMsg;

    private Integer retryCount;

    private String operator;

    private LocalDateTime createTime;

    private LocalDateTime finishTime;


    public Long getBatchNo() {
        return batchNo;
    }


    public void setBatchNo(Long batchNo) {
        this.batchNo = batchNo;
    }


    public String getRequestSeq() {
        return requestSeq;
    }


    public void setRequestSeq(String requestSeq) {
        this.requestSeq = requestSeq;
    }


    public String getCardType() {
        return cardType;
    }


    public void setCardType(String cardType) {
        this.cardType = cardType;
    }


    public String getAccTicketType() {
        return accTicketType;
    }


    public void setAccTicketType(String accTicketType) {
        this.accTicketType = accTicketType;
    }


    public Integer getRequestNum() {
        return requestNum;
    }


    public void setRequestNum(Integer requestNum) {
        this.requestNum = requestNum;
    }


    public String getSource() {
        return source;
    }


    public void setSource(String source) {
        this.source = source;
    }


    public String getFileName() {
        return fileName;
    }


    public void setFileName(String fileName) {
        this.fileName = fileName;
    }


    public String getFtpPath() {
        return ftpPath;
    }


    public void setFtpPath(String ftpPath) {
        this.ftpPath = ftpPath;
    }


    public Long getFileSize() {
        return fileSize;
    }


    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }


    public String getFileSha256() {
        return fileSha256;
    }


    public void setFileSha256(String fileSha256) {
        this.fileSha256 = fileSha256;
    }


    public Integer getTotalCount() {
        return totalCount;
    }


    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }


    public Integer getValidCount() {
        return validCount;
    }


    public void setValidCount(Integer validCount) {
        this.validCount = validCount;
    }


    public Integer getDuplicateCount() {
        return duplicateCount;
    }


    public void setDuplicateCount(Integer duplicateCount) {
        this.duplicateCount = duplicateCount;
    }


    public Integer getInvalidCount() {
        return invalidCount;
    }


    public void setInvalidCount(Integer invalidCount) {
        this.invalidCount = invalidCount;
    }


    public String getStatus() {
        return status;
    }


    public void setStatus(String status) {
        this.status = status;
    }


    public String getErrorMsg() {
        return errorMsg;
    }


    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }


    public Integer getRetryCount() {
        return retryCount;
    }


    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }


    public String getOperator() {
        return operator;
    }


    public void setOperator(String operator) {
        this.operator = operator;
    }


    public LocalDateTime getCreateTime() {
        return createTime;
    }


    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }


    public LocalDateTime getFinishTime() {
        return finishTime;
    }


    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }





}
