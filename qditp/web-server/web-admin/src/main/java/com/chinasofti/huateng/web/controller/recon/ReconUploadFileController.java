package com.chinasofti.huateng.web.controller.recon;

import java.io.File;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.chinasofti.huateng.common.annotation.Log;
import com.chinasofti.huateng.common.core.controller.BaseController;
import com.chinasofti.huateng.common.core.domain.AjaxResult;
import com.chinasofti.huateng.common.core.page.TableDataInfo;
import com.chinasofti.huateng.common.enums.BusinessType;
import com.chinasofti.huateng.common.utils.SecurityUtils;
import com.chinasofti.huateng.common.utils.StringUtils;
import com.chinasofti.huateng.common.utils.file.FileUtils;
import com.chinasofti.huateng.web.domain.recon.ReconUploadFile;
import com.chinasofti.huateng.web.service.recon.IReconUploadFileService;

/**
 * 对账文件上传管理
 */
@RestController
@RequestMapping("/recon/upload-file")
public class ReconUploadFileController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(ReconUploadFileController.class);

    /** 对账文件落盘目录，复用 web-admin.yaml 中 app-logs-v2 PVC 挂载点 /home/javaapp/app/export */
    public static final String UPLOAD_DIR = "/home/javaapp/app/export/recon-upload/";

    @Autowired
    private IReconUploadFileService reconUploadFileService;

    /**
     * 查询对账文件上传列表（按创建时间倒序）
     */
    @PreAuthorize("@ss.hasPermi('recon:upload-file:query')")
    @GetMapping("/list")
    public TableDataInfo list(ReconUploadFile file)
    {
        startPage();
        List<ReconUploadFile> list = reconUploadFileService.selectReconUploadFileList(file);
        return getDataTable(list);
    }

    /**
     * 上传对账文件
     */
    @PreAuthorize("@ss.hasPermi('recon:upload-file:upload')")
    @Log(title = "对账文件上传", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    public AjaxResult upload(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "reconDate", required = false) String reconDate,
                             @RequestParam(value = "remark", required = false) String remark)
    {
        if (file == null || file.isEmpty())
        {
            return AjaxResult.error("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isEmpty(originalFilename))
        {
            return AjaxResult.error("原始文件名为空");
        }

        String extension = StringUtils.substringAfterLast(originalFilename, ".");
        String baseName = StringUtils.contains(originalFilename, ".")
                ? StringUtils.substringBeforeLast(originalFilename, ".")
                : originalFilename;
        String safeBaseName = baseName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_");
        if (safeBaseName.length() > 80)
        {
            safeBaseName = safeBaseName.substring(0, 80);
        }
        String newFileName = StringUtils.isEmpty(extension)
                ? System.currentTimeMillis() + "_" + safeBaseName
                : System.currentTimeMillis() + "_" + safeBaseName + "." + extension;

        File destDir = new File(UPLOAD_DIR);
        if (!destDir.exists())
        {
            destDir.mkdirs();
        }

        String filePath = UPLOAD_DIR + newFileName;
        File dest = new File(filePath);
        try
        {
            file.transferTo(dest);
        }
        catch (Exception e)
        {
            log.error("保存对账文件失败, fileName={}", originalFilename, e);
            return AjaxResult.error("文件保存失败：" + e.getMessage());
        }

        ReconUploadFile record = new ReconUploadFile();
        record.setFileName(originalFilename);
        record.setFilePath(filePath);
        record.setFileSize(file.getSize());
        record.setFileType(StringUtils.lowerCase(extension));
        record.setReconDate(reconDate);
        record.setStatus("UPLOADED");
        record.setCreateBy(SecurityUtils.getUsername());
        record.setCreateTime(new Date());
        record.setRemark(remark);

        reconUploadFileService.insertReconUploadFile(record);

        AjaxResult ajax = AjaxResult.success("上传成功");
        ajax.put("id", record.getId());
        ajax.put("fileName", record.getFileName());
        ajax.put("filePath", record.getFilePath());
        return ajax;
    }

    /**
     * 下载对账文件
     */
    @PreAuthorize("@ss.hasPermi('recon:upload-file:download')")
    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response)
    {
        try
        {
            ReconUploadFile file = reconUploadFileService.selectReconUploadFileById(id);
            if (file == null)
            {
                throw new Exception("文件记录不存在");
            }
            String filePath = file.getFilePath();
            if (StringUtils.isEmpty(filePath))
            {
                throw new Exception("文件路径为空");
            }
            if (!filePath.startsWith(UPLOAD_DIR))
            {
                throw new Exception("非法文件路径");
            }
            File f = new File(filePath);
            if (!f.exists() || !f.isFile())
            {
                throw new Exception("文件不存在或已被清理");
            }

            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, file.getFileName());
            FileUtils.writeBytes(filePath, response.getOutputStream());
        }
        catch (Exception e)
        {
            log.error("下载对账文件失败, id={}", id, e);
        }
    }

    /**
     * 删除对账文件记录及磁盘文件
     */
    @PreAuthorize("@ss.hasPermi('recon:upload-file:remove')")
    @Log(title = "对账文件上传", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        ReconUploadFile file = reconUploadFileService.selectReconUploadFileById(id);
        if (file == null)
        {
            return AjaxResult.error("文件记录不存在");
        }
        String filePath = file.getFilePath();
        if (StringUtils.isNotEmpty(filePath) && filePath.startsWith(UPLOAD_DIR))
        {
            FileUtils.deleteFile(filePath);
        }
        return toAjax(reconUploadFileService.deleteReconUploadFileById(id));
    }
}
