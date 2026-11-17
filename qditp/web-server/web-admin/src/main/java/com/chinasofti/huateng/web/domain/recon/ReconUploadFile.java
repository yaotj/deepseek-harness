package com.chinasofti.huateng.web.domain.recon;

import java.util.Date;
import com.chinasofti.huateng.common.core.domain.BaseEntity;


public class ReconUploadFile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String fileType;


    private String fileCategory;

    private String reconDate;

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
