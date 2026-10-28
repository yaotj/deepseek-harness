package com.chinasofti.huateng.web.domain.importer;

import java.util.Date;
import com.chinasofti.huateng.common.core.domain.BaseEntity;

/**
 * 批量导入记录实体
 *
 * @author tuanjie
 */
public class BizImportRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 导入记录ID */
    private Long importId;

    /** 业务标识（对应 BizRouter 注册的 key） */
    private String bizCode;

    /** 业务名称（冗余展示） */
    private String bizName;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件存储路径 */
    private String fileUrl;

    /** 操作人 */
    private String importBy;

    /** 操作人部门ID */
    private Long deptId;

    /** 操作人IP */
    private String clientIp;

    /** 导入批次号（UUID，幂等键） */
    private String batchNo;

    /** 总行数（不含表头） */
    private Integer totalRows;

    /** 成功条数 */
    private Integer successRows;

    /** 失败条数 */
    private Integer failRows;

    /** 是否允许更新（0否 1是） */
    private String updateSupport;

    /** 导入状态（0处理中 1成功 2失败） */
    private String status;

    /** 完成时间 */
    private Date finishTime;

    /** 错误摘要 */
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
