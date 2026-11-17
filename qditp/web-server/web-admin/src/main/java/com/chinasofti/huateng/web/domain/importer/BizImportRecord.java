package com.chinasofti.huateng.web.domain.importer;

import java.util.Date;
import com.chinasofti.huateng.common.core.domain.BaseEntity;


public class BizImportRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long importId;

    private String bizCode;

    private String bizName;

    private String fileName;

    private Long fileSize;

    private String fileUrl;

    private String importBy;

    private Long deptId;

    private String clientIp;

    private String batchNo;

    private Integer totalRows;

    private Integer successRows;

    private Integer failRows;

    private String updateSupport;

    private String status;

    private Date finishTime;

    private String errorMsg;

    public Long getImportId()
    {
        return importId;
    }

    public void setImportId(Long importId)
    {
        this.importId = importId;
    }

    public String getBizCode()
    {
        return bizCode;
    }

    public void setBizCode(String bizCode)
    {
        this.bizCode = bizCode;
    }

    public String getBizName()
    {
        return bizName;
    }

    public void setBizName(String bizName)
    {
        this.bizName = bizName;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public Long getFileSize()
    {
        return fileSize;
    }

    public void setFileSize(Long fileSize)
    {
        this.fileSize = fileSize;
    }

    public String getFileUrl()
    {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl)
    {
        this.fileUrl = fileUrl;
    }

    public String getImportBy()
    {
        return importBy;
    }

    public void setImportBy(String importBy)
    {
        this.importBy = importBy;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    public String getClientIp()
    {
        return clientIp;
    }

    public void setClientIp(String clientIp)
    {
        this.clientIp = clientIp;
    }

    public String getBatchNo()
    {
        return batchNo;
    }

    public void setBatchNo(String batchNo)
    {
        this.batchNo = batchNo;
    }

    public Integer getTotalRows()
    {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows)
    {
        this.totalRows = totalRows;
    }

    public Integer getSuccessRows()
    {
        return successRows;
    }

    public void setSuccessRows(Integer successRows)
    {
        this.successRows = successRows;
    }

    public Integer getFailRows()
    {
        return failRows;
    }

    public void setFailRows(Integer failRows)
    {
        this.failRows = failRows;
    }

    public String getUpdateSupport()
    {
        return updateSupport;
    }

    public void setUpdateSupport(String updateSupport)
    {
        this.updateSupport = updateSupport;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Date getFinishTime()
    {
        return finishTime;
    }

    public void setFinishTime(Date finishTime)
    {
        this.finishTime = finishTime;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }
}
