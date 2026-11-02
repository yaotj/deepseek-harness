package com.chinasofti.huateng.web.domain.export;

import java.util.Date;
import com.chinasofti.huateng.common.core.domain.BaseEntity;

/**
 * 异步导出任务实体。
 */
public class AsyncExportTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long id;

    /** 任务编码：KEY_VERSION / CARD_POOL / ... */
    private String taskCode;

    /** 任务中文名 */
    private String taskName;

    /** 状态：PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    /** 查询条件 JSON */
    private String paramJson;

    /** 生成文件绝对路径 */
    private String filePath;

    /** 文件字节数 */
    private Long fileSize;

    /** 导出行数 */
    private Long rowCount;

    /** 失败原因 */
    private String errorMsg;

    /** 实际执行开始时间 */
    private Date executeTime;

    /** 完成时间 */
    private Date finishTime;

    /** 重试次数 */
    private Integer retryCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getParamJson() {
        return paramJson;
    }

    public void setParamJson(String paramJson) {
        this.paramJson = paramJson;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Date getExecuteTime() {
        return executeTime;
    }

    public void setExecuteTime(Date executeTime) {
        this.executeTime = executeTime;
    }

    public Date getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(Date finishTime) {
        this.finishTime = finishTime;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }
}
