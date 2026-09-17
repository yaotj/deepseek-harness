package com.chinasofti.huateng.cardpool.entity;

import java.time.LocalDateTime;

/** 逻辑卡号批次及 ACC 文件导入记录，对应表 LOGIC_CARD_POOL_BATCH。 */
public class LogicCardPoolBatch {

    /** 批次号，同时作为 ACC 请求流水号的数值来源。 */
    private Long batchNo;

    /** ACC 请求流水号。 */
    private String requestSeq;

    /** 票种，4 位。 */
    private String cardType;

    /** ACC 票种，票种后两位。 */
    private String accTicketType;

    /** 本批次申请数量。 */
    private Integer requestNum;

    /** 申请来源，AUTO 或 MANUAL。 */
    private String source;

    /** ACC 返回的逻辑卡号文件名。 */
    private String fileName;

    /** FTP 目录。 */
    private String ftpPath;

    /** 文件字节数。 */
    private Long fileSize;

    /** 文件 SHA-256 摘要。 */
    private String fileSha256;

    /** 文件总行数。 */
    private Integer totalCount;

    /** 成功入库的卡号数。 */
    private Integer validCount;

    /** 重复卡号数。 */
    private Integer duplicateCount;

    /** 格式非法的行数。 */
    private Integer invalidCount;

    /** 批次状态。 */
    private String status;

    /** 失败原因，最长 1900 字符。 */
    private String errorMsg;

    /** 重试次数。 */
    private Integer retryCount;

    /** 操作人，自动补货时为 SYSTEM。 */
    private String operator;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 完成时间，成功与失败都会写入。 */
    private LocalDateTime finishTime;

    /**
     * 读取批次号。
     *
     * @return 批次号，同时作为 ACC 请求流水号的数值来源
     */
    public Long getBatchNo() {
        return batchNo;
    }

    /**
     * 设置批次号。
     *
     * @param batchNo 批次号，由序列生成，同时作为 ACC 请求流水号的数值来源
     */
    public void setBatchNo(Long batchNo) {
        this.batchNo = batchNo;
    }

    /**
     * 读取 ACC 请求流水号。
     *
     * @return 向 ACC 申请逻辑卡号时使用的请求流水号
     */
    public String getRequestSeq() {
        return requestSeq;
    }

    /**
     * 设置 ACC 请求流水号。
     *
     * @param requestSeq 向 ACC 申请逻辑卡号时使用的请求流水号
     */
    public void setRequestSeq(String requestSeq) {
        this.requestSeq = requestSeq;
    }

    /**
     * 读取票种码。
     *
     * @return 4 位票种码，形如 044X
     */
    public String getCardType() {
        return cardType;
    }

    /**
     * 设置票种码。
     *
     * @param cardType 4 位票种码，形如 044X
     */
    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    /**
     * 读取 ACC 侧票种码。
     *
     * @return ACC 侧 2 位票种码，取票种码后两位
     */
    public String getAccTicketType() {
        return accTicketType;
    }

    /**
     * 设置 ACC 侧票种码。
     *
     * @param accTicketType ACC 侧 2 位票种码，取票种码后两位
     */
    public void setAccTicketType(String accTicketType) {
        this.accTicketType = accTicketType;
    }

    /**
     * 读取本批次申请数量。
     *
     * @return 向 ACC 申请的逻辑卡号数量
     */
    public Integer getRequestNum() {
        return requestNum;
    }

    /**
     * 设置本批次申请数量。
     *
     * @param requestNum 向 ACC 申请的逻辑卡号数量
     */
    public void setRequestNum(Integer requestNum) {
        this.requestNum = requestNum;
    }

    /**
     * 读取申请来源。
     *
     * @return AUTO（库存不足自动补货）或 MANUAL（后台人工发起）
     */
    public String getSource() {
        return source;
    }

    /**
     * 设置申请来源。
     *
     * @param source AUTO（库存不足自动补货）或 MANUAL（后台人工发起）
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * 读取 ACC 返回的逻辑卡号文件名。
     *
     * @return 逻辑卡号文件名，FAILED 批次若已有该值可重试下载
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 设置 ACC 返回的逻辑卡号文件名。
     *
     * @param fileName 逻辑卡号文件名，由 ACC 申请响应返回
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * 读取 FTP 目录。
     *
     * @return 逻辑卡号文件在 ACC FTP 上的所在目录
     */
    public String getFtpPath() {
        return ftpPath;
    }

    /**
     * 设置 FTP 目录。
     *
     * @param ftpPath 逻辑卡号文件在 ACC FTP 上的所在目录
     */
    public void setFtpPath(String ftpPath) {
        this.ftpPath = ftpPath;
    }

    /**
     * 读取文件字节数。
     *
     * @return 下载到本地的逻辑卡号文件字节数
     */
    public Long getFileSize() {
        return fileSize;
    }

    /**
     * 设置文件字节数。
     *
     * @param fileSize 下载到本地的逻辑卡号文件字节数
     */
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * 读取文件摘要。
     *
     * @return 逻辑卡号文件的 SHA-256 摘要，用于重复导入校验
     */
    public String getFileSha256() {
        return fileSha256;
    }

    /**
     * 设置文件摘要。
     *
     * @param fileSha256 逻辑卡号文件的 SHA-256 摘要，用于重复导入校验
     */
    public void setFileSha256(String fileSha256) {
        this.fileSha256 = fileSha256;
    }

    /**
     * 读取文件总行数。
     *
     * @return 逻辑卡号文件解析出的总行数
     */
    public Integer getTotalCount() {
        return totalCount;
    }

    /**
     * 设置文件总行数。
     *
     * @param totalCount 逻辑卡号文件解析出的总行数
     */
    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    /**
     * 读取成功入库的卡号数。
     *
     * @return 成功写入 LOGIC_CARD_POOL_CARD 的卡号数量
     */
    public Integer getValidCount() {
        return validCount;
    }

    /**
     * 设置成功入库的卡号数。
     *
     * @param validCount 成功写入 LOGIC_CARD_POOL_CARD 的卡号数量
     */
    public void setValidCount(Integer validCount) {
        this.validCount = validCount;
    }

    /**
     * 读取重复卡号数。
     *
     * @return 因卡号唯一约束被跳过的行数
     */
    public Integer getDuplicateCount() {
        return duplicateCount;
    }

    /**
     * 设置重复卡号数。
     *
     * @param duplicateCount 因卡号唯一约束被跳过的行数
     */
    public void setDuplicateCount(Integer duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    /**
     * 读取格式非法的行数。
     *
     * @return 卡号格式校验不通过而丢弃的行数
     */
    public Integer getInvalidCount() {
        return invalidCount;
    }

    /**
     * 设置格式非法的行数。
     *
     * @param invalidCount 卡号格式校验不通过而丢弃的行数
     */
    public void setInvalidCount(Integer invalidCount) {
        this.invalidCount = invalidCount;
    }

    /**
     * 读取批次状态。
     *
     * @return CREATED / REQUESTING / DOWNLOADING / IMPORTING / SUCCESS / FAILED / RETRYING
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置批次状态。
     *
     * @param status CREATED / REQUESTING / DOWNLOADING / IMPORTING / SUCCESS / FAILED / RETRYING
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 读取失败原因。
     *
     * @return 失败原因文本，最长 1900 字符，仅 FAILED 批次有值
     */
    public String getErrorMsg() {
        return errorMsg;
    }

    /**
     * 设置失败原因。
     *
     * @param errorMsg 失败原因文本，最长 1900 字符，超长需截断后写入
     */
    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    /**
     * 读取重试次数。
     *
     * @return 该批次已重试的次数，用于限制重复下载与导入
     */
    public Integer getRetryCount() {
        return retryCount;
    }

    /**
     * 设置重试次数。
     *
     * @param retryCount 该批次已重试的次数
     */
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * 读取操作人。
     *
     * @return 发起该批次的操作人账号，自动补货时为 SYSTEM
     */
    public String getOperator() {
        return operator;
    }

    /**
     * 设置操作人。
     *
     * @param operator 发起该批次的操作人账号，自动补货时传 SYSTEM
     */
    public void setOperator(String operator) {
        this.operator = operator;
    }

    /**
     * 读取创建时间。
     *
     * @return 批次创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间。
     *
     * @param createTime 批次创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 读取完成时间。
     *
     * @return 批次结束时间，SUCCESS 与 FAILED 都会写入
     */
    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    /**
     * 设置完成时间。
     *
     * @param finishTime 批次结束时间，进入 SUCCESS 或 FAILED 时写入
     */
    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }





}
