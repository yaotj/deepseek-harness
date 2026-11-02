package com.chinasofti.huateng.web.controller.monitor;

import java.io.File;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinasofti.huateng.common.annotation.Log;
import com.chinasofti.huateng.common.core.controller.BaseController;
import com.chinasofti.huateng.common.core.domain.AjaxResult;
import com.chinasofti.huateng.common.core.page.TableDataInfo;
import com.chinasofti.huateng.common.enums.BusinessType;
import com.chinasofti.huateng.common.utils.SecurityUtils;
import com.chinasofti.huateng.common.utils.StringUtils;
import com.chinasofti.huateng.common.utils.file.FileUtils;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;
import com.chinasofti.huateng.web.service.export.IAsyncExportTaskService;

/**
 * 异步导出任务管理
 */
@RestController
@RequestMapping("/monitor/async-export")
public class AsyncExportTaskController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(AsyncExportTaskController.class);

    /** 异步导出文件落盘目录，须与 web-admin.yaml 中 app-logs-v2 PVC 挂载点一致 */
    public static final String EXPORT_DIR = "/home/javaapp/app/export/";

    @Autowired
    private IAsyncExportTaskService asyncExportTaskService;

    /**
     * 查询异步导出任务列表
     */
    @PreAuthorize("@ss.hasPermi('monitor:async-export:query')")
    @GetMapping("/list")
    public TableDataInfo list(AsyncExportTask task)
    {
        startPage();
        List<AsyncExportTask> list = asyncExportTaskService.selectAsyncExportTaskList(task);
        return getDataTable(list);
    }

    /**
     * 提交异步导出任务（幂等：同用户同条件同编码的 PENDING 任务只保留一条）
     */
    @PreAuthorize("@ss.hasPermi('monitor:async-export:submit')")
    @Log(title = "异步导出任务", businessType = BusinessType.INSERT)
    @PostMapping("/submit")
    public AjaxResult submit(@RequestBody AsyncExportTask task)
    {
        if (StringUtils.isEmpty(task.getTaskCode()))
        {
            return AjaxResult.error("任务编码不能为空");
        }
        if (StringUtils.isEmpty(task.getTaskName()))
        {
            return AjaxResult.error("任务名称不能为空");
        }

        String username = SecurityUtils.getUsername();
        task.setCreateBy(username);
        task.setStatus("PENDING");
        if (StringUtils.isEmpty(task.getParamJson()))
        {
            task.setParamJson("{}");
        }

        AsyncExportTask exist = asyncExportTaskService.selectPendingTaskByUniqueKey(task);
        if (exist != null)
        {
            AjaxResult ajax = AjaxResult.success("已存在相同条件的待导出任务");
            ajax.put("taskId", exist.getId());
            return ajax;
        }

        task.setRetryCount(0);
        task.setCreateTime(new Date());
        asyncExportTaskService.insertAsyncExportTask(task);

        AjaxResult ajax = AjaxResult.success("提交成功，请前往任务列表查看");
        ajax.put("taskId", task.getId());
        return ajax;
    }

    /**
     * 下载生成的导出文件
     */
    @PreAuthorize("@ss.hasPermi('monitor:async-export:download')")
    @GetMapping("/download/{taskId}")
    public void download(@PathVariable Long taskId, HttpServletResponse response)
    {
        try
        {
            AsyncExportTask task = asyncExportTaskService.selectAsyncExportTaskById(taskId);
            if (task == null)
            {
                throw new Exception("任务不存在");
            }
            if (!"SUCCESS".equals(task.getStatus()))
            {
                throw new Exception("任务尚未完成，无法下载");
            }
            String filePath = task.getFilePath();
            if (StringUtils.isEmpty(filePath))
            {
                throw new Exception("文件路径为空");
            }
            if (!filePath.startsWith(EXPORT_DIR))
            {
                throw new Exception("非法文件路径");
            }
            File file = new File(filePath);
            if (!file.exists() || !file.isFile())
            {
                throw new Exception("文件不存在或已被清理");
            }

            String downloadName = StringUtils.substringAfterLast(filePath, "/");
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, downloadName);
            FileUtils.writeBytes(filePath, response.getOutputStream());
        }
        catch (Exception e)
        {
            log.error("下载异步导出文件失败, taskId={}", taskId, e);
        }
    }
}
