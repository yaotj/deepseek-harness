package com.chinasofti.huateng.web.domain.recon;

import java.util.Date;
import com.chinasofti.huateng.common.core.domain.BaseEntity;

/**
 * 对账文件上传记录
 */
public class ReconUploadFile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 文件ID */
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 文件存储绝对路径 */
    private String filePath;

    /** 文件字节数 */
    private Long fileSize;

    /** 文件类型（扩展名） */
    private String fileType;

    /**
     * 文件类别：EXP / PAY / BUS / DETAIL / TXNDIFF（ITP 侧五类）、BILL（渠道日账单）。
     * 命名规则上线前的历史行为 null，不参与 (类别, 对账日期) 唯一约束。
     */
    private String fileCategory;

    /** 对账日期（yyyyMMdd，可选） */
    private String reconDate;

    /** 状态：UPLOADED 已上传 */
    private String status;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getFilePath()
    {
        return filePath;
    }

    public void setFilePath(String filePath)
    {
        this.filePath = filePath;
    }

    public Long getFileSize()
    {
        return fileSize;
    }

    public void setFileSize(Long fileSize)
    {
        this.fileSize = fileSize;
    }

    public String getFileType()
    {
        return fileType;
    }

    public void setFileType(String fileType)
    {
        this.fileType = fileType;
    }

    public String getFileCategory()
    {
        return fileCategory;
    }

    public void setFileCategory(String fileCategory)
    {
        this.fileCategory = fileCategory;
    }

    public String getReconDate()
    {
        return reconDate;
    }

    public void setReconDate(String reconDate)
    {
        this.reconDate = reconDate;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}
