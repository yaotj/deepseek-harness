package com.chinasofti.huateng.model.cardpool;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 卡池批次异步导出 DTO。
 */
public class CardPoolBatchExportDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long batchNo;
    private String cardType;
    private String accTicketType;
    private String source;
    private String fileName;
    private Integer requestNum;
    private Integer validCount;
    private Integer duplicateCount;
    private Integer invalidCount;
    private String status;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;

    public Long getBatchNo()
    {
        return batchNo;
    }

    public void setBatchNo(Long batchNo)
    {
        this.batchNo = batchNo;
    }

    public String getCardType()
    {
        return cardType;
    }

    public void setCardType(String cardType)
    {
        this.cardType = cardType;
    }

    public String getAccTicketType()
    {
        return accTicketType;
    }

    public void setAccTicketType(String accTicketType)
    {
        this.accTicketType = accTicketType;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public Integer getRequestNum()
    {
        return requestNum;
    }

    public void setRequestNum(Integer requestNum)
    {
        this.requestNum = requestNum;
    }

    public Integer getValidCount()
    {
        return validCount;
    }

    public void setValidCount(Integer validCount)
    {
        this.validCount = validCount;
    }

    public Integer getDuplicateCount()
    {
        return duplicateCount;
    }

    public void setDuplicateCount(Integer duplicateCount)
    {
        this.duplicateCount = duplicateCount;
    }

    public Integer getInvalidCount()
    {
        return invalidCount;
    }

    public void setInvalidCount(Integer invalidCount)
    {
        this.invalidCount = invalidCount;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime)
    {
        this.createTime = createTime;
    }

    public LocalDateTime getFinishTime()
    {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime)
    {
        this.finishTime = finishTime;
    }
}
